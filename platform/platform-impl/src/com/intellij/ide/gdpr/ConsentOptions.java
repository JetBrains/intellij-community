// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ConsentOptions {
  private static final Logger LOG = Logger.getInstance(ConsentOptions.class);
  private static final String CONSENTS_CONFIRMATION_PROPERTY = "jb.consents.confirmation.enabled";
  private static final String STATISTICS_OPTION_ID = "rsch.send.usage.stat";
  private final boolean myIsEAP;

  private static @NotNull String getBundledResourcePath() {
    final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    return appInfo.isVendorJetBrains() ? "/consents.json" : "/consents-" + appInfo.getShortCompanyName() + ".json";
  }

  private static final class InstanceHolder {
    static final ConsentOptions ourInstance;
    static {
      final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      ourInstance = new ConsentOptions(new IOBackend() {
        private final File DEFAULT_CONSENTS_FILE = PathManager.getCommonDataPath().resolve(ApplicationNamesInfo.getInstance().getLowercaseProductName()).resolve("consentOptions").resolve("cached").toFile();
        private final File CONFIRMED_CONSENTS_FILE = PathManager.getCommonDataPath().resolve("consentOptions").resolve("accepted").toFile();
        private final String BUNDLED_CONSENTS_PATH = getBundledResourcePath();

        @Override
        public void writeDefaultConsents(@NotNull String data) throws IOException {
          FileUtil.writeToFile(DEFAULT_CONSENTS_FILE, data);
        }

        @Override
        public @NotNull String readDefaultConsents() throws IOException {
          return loadText(new FileInputStream(DEFAULT_CONSENTS_FILE));
        }

        @Override
        public @NotNull String readBundledConsents() {
          return loadText(ConsentOptions.class.getResourceAsStream(BUNDLED_CONSENTS_PATH));
        }

        @Override
        public void writeConfirmedConsents(@NotNull String data) throws IOException {
          FileUtil.writeToFile(CONFIRMED_CONSENTS_FILE, data);
        }

        @Override
        public @NotNull String readConfirmedConsents() throws IOException {
          return loadText(new FileInputStream(CONFIRMED_CONSENTS_FILE));
        }

        private @NotNull String loadText(InputStream stream) {
          if (stream != null) {
            try (Reader reader = new InputStreamReader(CharsetToolkit.inputStreamSkippingBOM(new BufferedInputStream(stream)),
                                                       StandardCharsets.UTF_8)) {
              return new String(FileUtil.adaptiveLoadText(reader));
            }
            catch (IOException e) {
              LOG.info(e);
            }
          }
          return "";
        }
      }, appInfo.isEAP() && appInfo.isVendorJetBrains());
    }
  }

  private final IOBackend myBackend;

  ConsentOptions(IOBackend backend, final boolean isEap) {
    myBackend = backend;
    myIsEAP = isEap;
  }

  public static ConsentOptions getInstance() {
    return InstanceHolder.ourInstance;
  }

  // here we have some well-known consents
  public enum Permission {
    YES, NO, UNDEFINED
  }

  public boolean isEAP() {
    return myIsEAP;
  }

  public @Nullable Consent getUsageStatsConsent() {
    return loadDefaultConsents().get(STATISTICS_OPTION_ID);
  }

  /**
   * Warning: For JetBrains products this setting is relevant for release builds only.
   * Statistics sending for JetBrains EAP builds is managed by a separate flag.
   */
  public Permission isSendingUsageStatsAllowed() {
    final ConfirmedConsent confirmedConsent = getConfirmedConsent(STATISTICS_OPTION_ID);
    return confirmedConsent == null? Permission.UNDEFINED : confirmedConsent.isAccepted()? Permission.YES : Permission.NO;
  }

  /**
   * Warning: For JetBrains products this setting is relevant for release builds only.
   * Statistics sending for JetBrains EAP builds is managed by a separate flag.
   */
  public boolean setSendingUsageStatsAllowed(boolean allowed) {
    final Consent defConsent = loadDefaultConsents().get(STATISTICS_OPTION_ID);
    if (defConsent != null && !defConsent.isDeleted()) {
      saveConfirmedConsents(Collections.singleton(new ConfirmedConsent(defConsent.getId(), defConsent.getVersion(), allowed, 0L)));
      return true;
    }
    return false;
  }

  public @Nullable String getConfirmedConsentsString() {
    final Map<String, Consent> defaults = loadDefaultConsents();
    if (!defaults.isEmpty()) {
      final String str = confirmedConsentToExternalString(
        loadConfirmedConsents().values().stream().filter(c -> {
          final Consent def = defaults.get(c.getId());
          return def != null && !def.isDeleted();
        })
      );
      return StringUtilRt.isEmptyOrSpaces(str)? null : str;
    }
    return null;
  }

  public void applyServerUpdates(@Nullable String json) {
    if (StringUtilRt.isEmptyOrSpaces(json)) {
      return;
    }

    try {
      final Collection<ConsentAttributes> fromServer = fromJson(json);
      // defaults
      final Map<String, Consent> defaults = loadDefaultConsents();
      if (applyServerChangesToDefaults(defaults, fromServer)) {
        myBackend.writeDefaultConsents(consentsToJson(defaults.values().stream()));
      }
      // confirmed consents
      final Map<String, ConfirmedConsent> confirmed = loadConfirmedConsents();
      if (applyServerChangesToConfirmedConsents(confirmed, fromServer)) {
        myBackend.writeConfirmedConsents(confirmedConsentToExternalString(confirmed.values().stream()));
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  public @NotNull Pair<List<Consent>, Boolean> getConsents() {
    final Map<String, Consent> allDefaults = loadDefaultConsents();
    if (myIsEAP) {
      // for EA builds there is a different option for statistics sending management
      allDefaults.remove(STATISTICS_OPTION_ID);
    }
    if (allDefaults.isEmpty()) {
      return new Pair<>(Collections.emptyList(), Boolean.FALSE);
    }
    final Map<String, ConfirmedConsent> allConfirmed = loadConfirmedConsents();
    final List<Consent> result = new ArrayList<>();
    for (Map.Entry<String, Consent> entry : allDefaults.entrySet()) {
      final Consent base = entry.getValue();
      if (!base.isDeleted()) {
        final ConfirmedConsent confirmed = allConfirmed.get(base.getId());
        result.add(confirmed == null? base : base.derive(confirmed.isAccepted()));
      }
    }
    result.sort(Comparator.comparing(ConsentBase::getId));
    boolean confirmationEnabled = Boolean.parseBoolean(System.getProperty(CONSENTS_CONFIRMATION_PROPERTY, "true"));
    return new Pair<>(result, confirmationEnabled && needReconfirm(allDefaults, allConfirmed));
  }

  public void setConsents(@NotNull Collection<Consent> confirmedByUser) {
    saveConfirmedConsents(
      ContainerUtil.map(confirmedByUser, c -> new ConfirmedConsent(c.getId(), c.getVersion(), c.isAccepted(), 0L))
    );
  }

  private @Nullable ConfirmedConsent getConfirmedConsent(String consentId) {
    final Consent defConsent = loadDefaultConsents().get(consentId);
    if (defConsent != null && defConsent.isDeleted()) {
      return null;
    }
    return loadConfirmedConsents().get(consentId);
  }

  private void saveConfirmedConsents(@NotNull Collection<ConfirmedConsent> updates) {
    if (!updates.isEmpty()) {
      try {
        final Map<String, ConfirmedConsent> allConfirmed = loadConfirmedConsents();
        final long stamp = System.currentTimeMillis();
        for (ConfirmedConsent consent : updates) {
          consent.setAcceptanceTime(stamp);
          allConfirmed.put(consent.getId(), consent);
        }
        myBackend.writeConfirmedConsents(confirmedConsentToExternalString(allConfirmed.values().stream()));
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
  }

  private static boolean needReconfirm(Map<String, Consent> defaults, Map<String, ConfirmedConsent> confirmed) {
    for (Consent defConsent : defaults.values()) {
      if (defConsent.isDeleted()) {
        continue;
      }
      final ConfirmedConsent confirmedConsent = confirmed.get(defConsent.getId());
      if (confirmedConsent == null) {
        return true;
      }
      final Version confirmedVersion = confirmedConsent.getVersion();
      final Version defaultVersion = defConsent.getVersion();
      // consider only major version differences
      if (confirmedVersion.isOlder(defaultVersion) && confirmedVersion.getMajor() != defaultVersion.getMajor()) {
        return true;
      }
    }
    return false;
  }

  private static boolean applyServerChangesToConfirmedConsents(Map<String, ConfirmedConsent> base, Collection<? extends ConsentAttributes> fromServer) {
    boolean changes = false;
    for (ConsentAttributes update : fromServer) {
      final ConfirmedConsent current = base.get(update.consentId);
      if (current != null) {
        final ConfirmedConsent change = new ConfirmedConsent(update);
        if (!change.getVersion().isOlder(current.getVersion()) && current.getAcceptanceTime() < update.acceptanceTime) {
          base.put(change.getId(), change);
          changes = true;
        }
      }
    }
    return changes;
  }

  private static boolean applyServerChangesToDefaults(@NotNull Map<String, Consent> base, @NotNull Collection<ConsentAttributes> fromServer) {
    boolean changes = false;
    for (ConsentAttributes update : fromServer) {
      final Consent newConsent = new Consent(update);
      final Consent current = base.get(newConsent.getId());
      if (current == null || newConsent.getVersion().isNewer(current.getVersion()) || newConsent.isDeleted() != current.isDeleted()) {
        base.put(newConsent.getId(), newConsent);
        changes = true;
      }
    }
    return changes;
  }

  private static @NotNull Collection<ConsentAttributes> fromJson(@Nullable String json) {
    if (StringUtilRt.isEmptyOrSpaces(json)) {
      return Collections.emptyList();
    }

    List<ConsentAttributes> result = new ArrayList<>();
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.beginArray();
      while (reader.hasNext()) {
        result.add(readConsentAttributes(reader));
      }
      reader.endArray();
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return result;
  }

  private static @NotNull ConsentAttributes readConsentAttributes(@NotNull JsonReader reader) throws IOException {
    ConsentAttributes attributes = new ConsentAttributes();
    reader.beginObject();
    while (reader.hasNext()) {
      switch (reader.nextName()) {
        case "consentId":
          attributes.consentId = reader.nextString();
          break;
        case "version":
          attributes.version = reader.nextString();
          break;
        case "text":
          attributes.text = reader.nextString();
          break;
        case "printableName":
          attributes.printableName = reader.nextString();
          break;
        case "accepted":
          // JSON is not valid - boolean value maybe specified as string true/false
          attributes.accepted = reader.peek() == JsonToken.STRING ? Boolean.parseBoolean(reader.nextString()) : reader.nextBoolean();
          break;
        case "deleted":
          attributes.deleted = reader.peek() == JsonToken.STRING ? Boolean.parseBoolean(reader.nextString()) : reader.nextBoolean();
          break;
        case "acceptanceTime":
          attributes.acceptanceTime = reader.nextLong();
          break;

        default:
          // skip unknown field
          reader.skipValue();
          break;
      }
    }
    reader.endObject();
    return attributes;
  }

  private static @NotNull String consentsToJson(@NotNull Stream<Consent> consents) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    return gson.toJson(consents.map(Consent::toConsentAttributes).toArray());
  }

  private static @NotNull String confirmedConsentToExternalString(@NotNull Stream<ConfirmedConsent> consents) {
    return consents/*.sorted(Comparator.comparing(confirmedConsent -> confirmedConsent.getId()))*/.map(ConfirmedConsent::toExternalString).collect(Collectors.joining(";"));
  }

  private @NotNull Map<String, Consent> loadDefaultConsents() {
    final Map<String, Consent> result = new HashMap<>();
    for (ConsentAttributes attributes : fromJson(myBackend.readBundledConsents())) {
      result.put(attributes.consentId, new Consent(attributes));
    }
    try {
      applyServerChangesToDefaults(result, fromJson(myBackend.readDefaultConsents()));
    }
    catch (IOException ignored) {
    }
    return result;
  }

  private @NotNull Map<String, ConfirmedConsent> loadConfirmedConsents() {
    final Map<String, ConfirmedConsent> result = new HashMap<>();
    try {
      final StringTokenizer tokenizer = new StringTokenizer(myBackend.readConfirmedConsents(), ";", false);
      while (tokenizer.hasMoreTokens()) {
        final ConfirmedConsent consent = ConfirmedConsent.fromString(tokenizer.nextToken());
        if (consent != null) {
          result.put(consent.getId(), consent);
        }
      }
    }
    catch (IOException ignored) {
    }
    return result;
  }

  protected interface IOBackend {
    void writeDefaultConsents(@NotNull String data) throws IOException;
    @NotNull
    String readDefaultConsents() throws IOException;
    @NotNull
    String readBundledConsents();

    void writeConfirmedConsents(@NotNull String data) throws IOException;
    @NotNull
    String readConfirmedConsents() throws IOException;
  }
}