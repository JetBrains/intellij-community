/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.gdpr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Eugene Zhuravlev
 * Date: 05-Dec-17
 */
public final class ConsentOptions {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.gdpr.ConsentOptions");
  private static final String CONSENTS_CONFIRMATION_PROPERTY = "jb.consents.confirmation.enabled";
  private static final String BUNDLED_RESOURCE_PATH = "/consents.json";
  private static final String STATISTICS_OPTION_ID = "rsch.send.usage.stat";

  private static final class InstanceHolder {
    static final ConsentOptions ourInstance = new ConsentOptions(new IOBackend() {
      private final File DEFAULT_CONSENTS_FILE = new File(Locations.getDataRoot(), ApplicationNamesInfo.getInstance().getLowercaseProductName() + "/consentOptions/cached");
      private final File CONFIRMED_CONSENTS_FILE = new File(Locations.getDataRoot(), "/consentOptions/accepted");

      public void writeDefaultConsents(@NotNull String data) throws IOException {
        FileUtil.writeToFile(DEFAULT_CONSENTS_FILE, data);
      }

      @NotNull
      public String readDefaultConsents() throws IOException {
        return loadText(new FileInputStream(DEFAULT_CONSENTS_FILE));
      }

      @NotNull
      public String readBundledConsents() {
        return loadText(ConsentOptions.class.getResourceAsStream(BUNDLED_RESOURCE_PATH));
      }

      public void writeConfirmedConsents(@NotNull String data) throws IOException {
        FileUtil.writeToFile(CONFIRMED_CONSENTS_FILE, data);
      }

      @NotNull
      public String readConfirmedConsents() throws IOException {
        return loadText(new FileInputStream(CONFIRMED_CONSENTS_FILE));
      }

      @NotNull
      private String loadText(InputStream stream) {
        try {
          if (stream != null) {
            final Reader reader = new InputStreamReader(new BufferedInputStream(stream), StandardCharsets.UTF_8);
            try {
              return new String(FileUtil.adaptiveLoadText(reader));
            }
            finally {
              reader.close();
            }
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
        return "";
      }
    });
  }

  private final IOBackend myBackend;

  ConsentOptions(IOBackend backend) {
    myBackend = backend;
  }

  public static ConsentOptions getInstance() {
    return InstanceHolder.ourInstance;
  }

  // here we have some well-known consents
  public enum Permission {
    YES, NO, UNDEFINED
  }

  public Permission isSendingUsageStatsAllowed() {
    final ConfirmedConsent confirmedConsent = getConfirmedConsent(STATISTICS_OPTION_ID);
    return confirmedConsent == null? Permission.UNDEFINED : confirmedConsent.isAccepted()? Permission.YES : Permission.NO;
  }

  public boolean setSendingUsageStatsAllowed(boolean allowed) {
    final Consent defConsent = loadDefaultConsents().get(STATISTICS_OPTION_ID);
    if (defConsent != null && !defConsent.isDeleted()) {
      saveConfirmedConsents(Collections.singleton(new ConfirmedConsent(defConsent.getId(), defConsent.getVersion(), allowed, 0L)));
      return true;
    }
    return false;
  }

  @Nullable
  public String getConfirmedConsentsString() {
    final Map<String, Consent> defaults = loadDefaultConsents();
    if (!defaults.isEmpty()) {
      final String str = confirmedConsentToExternalString(
        loadConfirmedConsents().values().stream().filter(c -> {
          final Consent def = defaults.get(c.getId());
          return def != null && !def.isDeleted();
        })
      );
      return StringUtil.isEmptyOrSpaces(str)? null : str;
    }
    return null;
  }

  public void applyServerUpdates(@Nullable String json) {
    if (StringUtil.isEmptyOrSpaces(json)) {
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

  public Pair<Collection<Consent>, Boolean> getConsents() {
    final Map<String, Consent> allDefaults = loadDefaultConsents();
    if (allDefaults.isEmpty()) {
      return Pair.create(Collections.emptyList(), Boolean.FALSE);
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
    Collections.sort(result, Comparator.comparing(o -> o.getId()));
    final Boolean confirmationEnabled = Boolean.valueOf(System.getProperty(CONSENTS_CONFIRMATION_PROPERTY, "true"));
    return Pair.create(result, confirmationEnabled && needReconfirm(allDefaults, allConfirmed));
  }

  public void setConsents(Collection<Consent> confirmedByUser) {
    saveConfirmedConsents(
      confirmedByUser.stream().map(
        c -> new ConfirmedConsent(c.getId(), c.getVersion(), c.isAccepted(), 0L)
      ).collect(Collectors.toList())
    );
  }

  @Nullable
  private ConfirmedConsent getConfirmedConsent(String consentId) {
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

  private static boolean applyServerChangesToConfirmedConsents(Map<String, ConfirmedConsent> base, Collection<ConsentAttributes> fromServer) {
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

  private static boolean applyServerChangesToDefaults(Map<String, Consent> base, Collection<ConsentAttributes> fromServer) {
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

  @NotNull
  private static Collection<ConsentAttributes> fromJson(String json) {
    try {
      final ConsentAttributes[] data = StringUtil.isEmptyOrSpaces(json)? null : new GsonBuilder().disableHtmlEscaping().create().fromJson(json, ConsentAttributes[].class);
      if (data != null) {
        return Arrays.asList(data);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return Collections.emptyList();
  }

  private static String consentsToJson(Stream<Consent> consents) {
    return consentAttributesToJson(consents.map(consent -> consent.toConsentAttributes()));
  }
  
  private static String consentAttributesToJson(Stream<ConsentAttributes> attributes) {
    final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    return gson.toJson(attributes.toArray());
  }
  
  private static String confirmedConsentToExternalString(Stream<ConfirmedConsent> consents) {
    return StringUtil.join(consents/*.sorted(Comparator.comparing(confirmedConsent -> confirmedConsent.getId()))*/.map(c -> c.toExternalString()).collect(Collectors.toList()), ";");
  }

  @NotNull
  private Map<String, Consent> loadDefaultConsents() {
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

  @NotNull
  private Map<String, ConfirmedConsent> loadConfirmedConsents() {
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
