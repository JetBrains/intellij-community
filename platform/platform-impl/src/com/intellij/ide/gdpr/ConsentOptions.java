// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ConsentOptions {
  private static final Logger LOG = Logger.getInstance(ConsentOptions.class);
  private static final String CONSENTS_CONFIRMATION_PROPERTY = "jb.consents.confirmation.enabled";
  private static final String STATISTICS_OPTION_ID = "rsch.send.usage.stat";
  private static final String EAP_FEEDBACK_OPTION_ID = "eap";
  private static final Set<String> PER_PRODUCT_CONSENTS = Set.of(EAP_FEEDBACK_OPTION_ID);
  private final boolean myIsEAP;
  @Nullable
  private String myProductCodeSuffix;

  private static final class InstanceHolder {
    static final ConsentOptions ourInstance;
    static {
      final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      Path commonDataPath = PathManager.getCommonDataPath();
      ourInstance = new ConsentOptions(new IOBackend() {
        private final Path DEFAULT_CONSENTS_FILE = commonDataPath
          .resolve(ApplicationNamesInfo.getInstance().getLowercaseProductName())
          .resolve("consentOptions/cached");
        private final Path CONFIRMED_CONSENTS_FILE = commonDataPath.resolve("consentOptions/accepted");
        private final String BUNDLED_CONSENTS_PATH = getBundledResourcePath();

        @Override
        public void writeDefaultConsents(@NotNull String data) throws IOException {
          Files.createDirectories(DEFAULT_CONSENTS_FILE.getParent());
          Files.writeString(DEFAULT_CONSENTS_FILE, data);
        }

        @Override
        @NotNull
        public String readDefaultConsents() throws IOException {
          return loadText(Files.newInputStream(DEFAULT_CONSENTS_FILE));
        }

        @Override
        @NotNull
        public String readBundledConsents() {
          return loadText(ConsentOptions.class.getClassLoader().getResourceAsStream(BUNDLED_CONSENTS_PATH));
        }

        @Override
        public void writeConfirmedConsents(@NotNull String data) throws IOException {
          Files.createDirectories(CONFIRMED_CONSENTS_FILE.getParent());
          Files.writeString(CONFIRMED_CONSENTS_FILE, data);
        }

        @Override
        @NotNull
        public String readConfirmedConsents() throws IOException {
          return loadText(Files.newInputStream(CONFIRMED_CONSENTS_FILE));
        }

        @NotNull
        private String loadText(InputStream stream) {
          if (stream != null) {
            try (InputStream inputStream = CharsetToolkit.inputStreamSkippingBOM(stream)) {
              return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
              LOG.info(e);
            }
          }
          return "";
        }
      }, appInfo.isEAP() && appInfo.isVendorJetBrains());
    }

    @NotNull @NonNls
    private static String getBundledResourcePath() {
      final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      return appInfo.isVendorJetBrains() ? "consents.json" : "consents-" + appInfo.getShortCompanyName() + ".json";
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

  public void setProductCode(String code) {
    myProductCodeSuffix = code != null? "." + code.toLowerCase(Locale.ENGLISH) : null;
  }

  @Nullable
  public Consent getDefaultUsageStatsConsent() {
    return getDefaultConsent(STATISTICS_OPTION_ID);
  }

  @NotNull
  public static Predicate<Consent> condUsageStatsConsent() {
    return consent -> STATISTICS_OPTION_ID.equals(consent.getId());
  }

  @NotNull
  public static Predicate<Consent> condEAPFeedbackConsent() {
    return consent -> {
      final String id = consent.getId();
      return id.startsWith(EAP_FEEDBACK_OPTION_ID) && (id.length() == EAP_FEEDBACK_OPTION_ID.length() || id.charAt(EAP_FEEDBACK_OPTION_ID.length()) == '.');
    };
  }

  /**
   * Warning: For JetBrains products this setting is relevant for release builds only.
   * Statistics sending for JetBrains EAP builds is managed by a separate flag.
   */
  public Permission isSendingUsageStatsAllowed() {
    return getPermission(STATISTICS_OPTION_ID);
  }

  /**
   * Warning: For JetBrains products this setting is relevant for release builds only.
   * Statistics sending for JetBrains EAP builds is managed by a separate flag.
   */
  public boolean setSendingUsageStatsAllowed(boolean allowed) {
    return setPermission(STATISTICS_OPTION_ID, allowed);
  }

  public Permission isEAPFeedbackAllowed() {
    return getPermission(EAP_FEEDBACK_OPTION_ID);
  }

  public boolean setEAPFeedbackAllowed(boolean allowed) {
    return setPermission(EAP_FEEDBACK_OPTION_ID, allowed);
  }

  @NotNull
  private Permission getPermission(final String consentId) {
    final ConfirmedConsent confirmedConsent = getConfirmedConsent(consentId);
    return confirmedConsent == null? Permission.UNDEFINED : confirmedConsent.isAccepted()? Permission.YES : Permission.NO;
  }

  private boolean setPermission(final String consentId, boolean allowed) {
    final Consent defConsent = getDefaultConsent(consentId);
    if (defConsent != null && !defConsent.isDeleted()) {
      saveConfirmedConsents(Collections.singleton(new ConfirmedConsent(defConsent.getId(), defConsent.getVersion(), allowed, 0L)));
      return true;
    }
    return false;
  }

  private String lookupConsentID(String consentId) {
    final String suffix = myProductCodeSuffix;
    return suffix != null && PER_PRODUCT_CONSENTS.contains(consentId)? consentId + suffix : consentId;
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
      return str.isBlank()? null : str;
    }
    return null;
  }

  public void applyServerUpdates(@Nullable String json) {
    if (json == null || json.isBlank()) {
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
    return getConsents(consent -> true);
  }
  
  public @NotNull Pair<List<Consent>, Boolean> getConsents(@NotNull Predicate<Consent> filter) {
    final Map<String, Consent> allDefaults = loadDefaultConsents();
    if (myIsEAP) {
      // for EA builds there is a different option for statistics sending management
      allDefaults.remove(STATISTICS_OPTION_ID);
    }
    else {
      // EAP feedback consent is relevant to EA builds only
      allDefaults.remove(lookupConsentID(EAP_FEEDBACK_OPTION_ID));
    }

    for (Iterator<Map.Entry<String, Consent>> it = allDefaults.entrySet().iterator(); it.hasNext(); ) {
      final Map.Entry<String, Consent> entry = it.next();
      if (!filter.test(entry.getValue())) {
        it.remove();
      }
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
    List<ConfirmedConsent> result;
    if (confirmedByUser.isEmpty()) {
      result = Collections.emptyList();
    }
    else {
      List<ConfirmedConsent> list = new ArrayList<>(confirmedByUser.size());
      for (Consent t : confirmedByUser) {
        list.add(new ConfirmedConsent(t.getId(), t.getVersion(), t.isAccepted(), 0L));
      }
      result = list;
    }
    saveConfirmedConsents(result);
  }

  @Nullable
  private Consent getDefaultConsent(String consentId) {
    return loadDefaultConsents().get(lookupConsentID(consentId));
  }

  @Nullable
  private ConfirmedConsent getConfirmedConsent(String consentId) {
    final Consent defConsent = getDefaultConsent(consentId);
    if (defConsent != null && defConsent.isDeleted()) {
      return null;
    }
    return loadConfirmedConsents().get(defConsent != null? defConsent.getId() : lookupConsentID(consentId));
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

  public boolean needsReconfirm(Consent consent) {
    if (consent == null || consent.isDeleted() || myIsEAP && STATISTICS_OPTION_ID.equals(consent.getId())) {
      // for EA builds there is a different option for statistics sending management
      return false;
    }
    final ConfirmedConsent confirmedConsent = loadConfirmedConsents().get(consent.getId());
    if (confirmedConsent == null) {
      return true;
    }
    final Version confirmedVersion = confirmedConsent.getVersion();
    final Version defaultVersion = consent.getVersion();
    // consider only major version differences
    return confirmedVersion.isOlder(defaultVersion) && confirmedVersion.getMajor() != defaultVersion.getMajor();
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

  private static boolean applyServerChangesToDefaults(@NotNull Map<String, Consent> base, @NotNull Collection<ConsentAttributes> fromServer) {
    boolean changes = false;
    for (ConsentAttributes update : fromServer) {
      final Consent newConsent = new Consent(update);
      final Consent current = base.get(newConsent.getId());
      if (current != null && !newConsent.isDeleted() && newConsent.getVersion().isNewer(current.getVersion())) {
        base.put(newConsent.getId(), newConsent);
        changes = true;
      }
    }
    return changes;
  }

  @NotNull
  private Collection<ConsentAttributes> fromJson(@Nullable String json) {
    try {
      ConsentAttributes[] data = StringUtilRt.isEmptyOrSpaces(json) ? null : new GsonBuilder().disableHtmlEscaping().create().fromJson(json, ConsentAttributes[].class);
      if (data != null) {
        for (ConsentAttributes attributes : data) {
          attributes.consentId = lookupConsentID(attributes.consentId);
        }
        return Arrays.asList(data);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return Collections.emptyList();
  }

  @NotNull
  private String consentsToJson(@NotNull Stream<Consent> consents) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    final String suffix = myProductCodeSuffix;
    return gson.toJson(consents.map(consent -> {
      final ConsentAttributes attribs = consent.toConsentAttributes();
      if (suffix != null && attribs.consentId.endsWith(suffix)) {
        attribs.consentId = attribs.consentId.substring(0, attribs.consentId.length() - suffix.length());
      }
      return attribs;
    }).toArray());
  }

  @NotNull
  private static String confirmedConsentToExternalString(@NotNull Stream<ConfirmedConsent> consents) {
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