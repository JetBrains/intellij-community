// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.diagnostic.LoadingState;
import com.intellij.l10n.LocalizationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.CharsetToolkit;
import kotlin.Pair;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class ConsentOptions implements ModificationTracker {
  private static final Logger LOG = Logger.getInstance(ConsentOptions.class);

  private static final String CONSENTS_CONFIRMATION_PROPERTY = "jb.consents.confirmation.enabled";
  private static final String RECONFIRM_CONSENTS_PROPERTY = "test.force.reconfirm.consents";
  private static final String STATISTICS_OPTION_ID = "rsch.send.usage.stat";
  private static final String EAP_FEEDBACK_OPTION_ID = "eap";
  private static final String AI_DATA_COLLECTION_OPTION_ID = "ai.data.collection.and.use.policy";
  private static final Set<String> PER_PRODUCT_CONSENTS = Set.of(EAP_FEEDBACK_OPTION_ID);

  private final BooleanSupplier myIsEap;
  private String myProductCode;
  private Set<String> myPluginCodes = Set.of();
  private final AtomicLong myModificationCount = new AtomicLong();

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }

  private static Path getDefaultConsentsFile() {
    return PathManager.getCommonDataPath()
      .resolve(ApplicationNamesInfo.getInstance().getLowercaseProductName())
      .resolve("consentOptions/cached");
  }

  private static Path getConfirmedConsentsFile() {
    return PathManager.getCommonDataPath().resolve("consentOptions/accepted");
  }

  private static Locale getCurrentLocale() {
    return LocalizationUtil.INSTANCE.getLocale();
  }
  
  private static Locale getDefaultLocale() {
    return LocalizationUtil.INSTANCE.getDefaultLocale();
  }

  private static final class InstanceHolder {
    static final ConsentOptions ourInstance = new ConsentOptions(new IOBackend() {
      @Override
      public void writeDefaultConsents(@NotNull String data) throws IOException {
        var defaultConsentsFile = getDefaultConsentsFile();
        Files.createDirectories(defaultConsentsFile.getParent());
        Files.writeString(defaultConsentsFile, data);
      }

      @Override
      public @NotNull String readDefaultConsents() throws IOException {
        return loadText(Files.newInputStream(getDefaultConsentsFile()));
      }

      @Override
      public @NotNull String readBundledConsents() {
        return loadText(ConsentOptions.class.getClassLoader().getResourceAsStream(getBundledResourcePath()));
      }

      @Override
      public @Nullable String readLocalizedBundledConsents() {
        if (getCurrentLocale() == getDefaultLocale()) {
          return null;
        }

        for (var localizedPath : LocalizationUtil.INSTANCE.getLocalizedPaths(getBundledResourcePath(), getCurrentLocale())) {
          var loadedText = loadText(ConsentOptions.class.getClassLoader().getResourceAsStream(localizedPath));
          if (!loadedText.isEmpty()) {
            return loadedText;
          }
        }
        return null;
      }

      @Override
      public void writeConfirmedConsents(@NotNull String data) throws IOException {
        var confirmedConsentsFile = getConfirmedConsentsFile();
        Files.createDirectories(confirmedConsentsFile.getParent());
        Files.writeString(confirmedConsentsFile, data);
        if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
          ApplicationManager.getApplication().getMessageBus()
            .syncPublisher(DataSharingSettingsChangeListener.TOPIC)
            .consentWritten();
        }
      }

      @Override
      public @NotNull String readConfirmedConsents() throws IOException {
        return loadText(Files.newInputStream(getConfirmedConsentsFile()));
      }

      private static String loadText(InputStream stream) {
        if (stream != null) {
          try (var inputStream = CharsetToolkit.inputStreamSkippingBOM(stream)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
        return "";
      }
    });

    private static String getBundledResourcePath() {
      if ("JetBrains".equals(System.getProperty("idea.vendor.name"))) {
        return "consents.json";
      }

      var appInfo = ApplicationInfoImpl.getShadowInstance();
      return appInfo.isVendorJetBrains() ? "consents.json" : "consents-" + appInfo.getShortCompanyName() + ".json";
    }
  }

  private final IOBackend myBackend;

  @VisibleForTesting
  public ConsentOptions(IOBackend backend, boolean isEap) {
    myBackend = backend;
    myIsEap = () -> isEap;
  }

  ConsentOptions(IOBackend backend) {
    myBackend = backend;
    myIsEap = () -> {
      var appInfo = ApplicationInfoImpl.getShadowInstance();
      return appInfo.isEAP() && appInfo.isVendorJetBrains() && !Agreements.isReleaseAgreementsEnabled();
    };
  }

  public static ConsentOptions getInstance() {
    return InstanceHolder.ourInstance;
  }

  public static boolean needToShowUsageStatsConsent() {
    return getInstance().getConsents(condUsageStatsConsent()).getSecond();
  }

  // here we have some well-known consents
  public enum Permission {
    YES, NO, UNDEFINED
  }

  public boolean isEAP() {
    return myIsEap.getAsBoolean();
  }

  public void setProductCode(String platformCode, Iterable<String> pluginCodes) {
    myProductCode = platformCode != null? platformCode.toLowerCase(getDefaultLocale()) : null;
    var codes = new HashSet<String>();
    for (var pluginCode : pluginCodes) {
      codes.add(pluginCode.toLowerCase(getDefaultLocale()));
    }
    myPluginCodes = codes.isEmpty()? Set.of() : Collections.unmodifiableSet(codes);
  }

  public @Nullable Consent getDefaultUsageStatsConsent() {
    return getDefaultConsent(STATISTICS_OPTION_ID);
  }

  public static @NotNull Predicate<Consent> condUsageStatsConsent() {
    return consent -> STATISTICS_OPTION_ID.equals(consent.getId());
  }

  public static @NotNull Predicate<Consent> condEAPFeedbackConsent() {
    return consent -> isProductConsentOfKind(EAP_FEEDBACK_OPTION_ID, consent.getId());
  }

  public static @NotNull Predicate<Consent> condAiDataCollectionConsent() {
    return consent -> AI_DATA_COLLECTION_OPTION_ID.equals(consent.getId());
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

  public @NotNull Permission getAiDataCollectionPermission() {
    return getPermission(AI_DATA_COLLECTION_OPTION_ID);
  }

  public void setAiDataCollectionPermission(boolean permitted) {
    setPermission(AI_DATA_COLLECTION_OPTION_ID, permitted);
  }

  private Permission getPermission(String consentId) {
    var confirmedConsent = getConfirmedConsent(consentId);
    return confirmedConsent == null? Permission.UNDEFINED : confirmedConsent.isAccepted()? Permission.YES : Permission.NO;
  }

  private boolean setPermission(String consentId, boolean allowed) {
    var defConsent = getDefaultConsent(consentId);
    if (defConsent != null && !defConsent.isDeleted()) {
      setConsents(Collections.singleton(defConsent.derive(allowed)));
      return true;
    }
    return false;
  }

  private String lookupConsentID(String consentId) {
    var productCode = myProductCode;
    return productCode != null && PER_PRODUCT_CONSENTS.contains(consentId)? consentId + "." + productCode : consentId;
  }

  public @Nullable String getConfirmedConsentsString() {
    var defaults = loadDefaultConsents();
    if (!defaults.isEmpty()) {
      var str = confirmedConsentToExternalString(
        loadConfirmedConsents().values().stream().filter(c -> {
          var defaultConsents = defaults.get(c.getId());
          var def = defaultConsents != null ? defaultConsents.get(getDefaultLocale()) : null;
          if (def != null) {
            return !def.isDeleted();
          }
          for (var prefix : PER_PRODUCT_CONSENTS) {
            // allow also JB plugin consents, which do not have corresponding 'direct' defaults
            if (isProductConsentOfKind(prefix, c.getId())) {
              return true;
            }
          }
          return false;
        })
      );
      if (!str.isBlank()) {
        return str;
      }
    }
    return null;
  }

  public void applyServerUpdates(@Nullable String json) {
    if (json == null || json.isBlank()) {
      return;
    }

    try {
      var fromServer = fromJson(json);
      // defaults
      var defaults = loadDefaultConsents();
      if (applyServerChangesToDefaults(defaults, fromServer)) {
        myBackend.writeDefaultConsents(consentsToJson(defaults.values().stream().flatMap(it -> it.values().stream())));
      }
      // confirmed consents
      var confirmed = loadConfirmedConsents();
      if (applyServerChangesToConfirmedConsents(confirmed, fromServer)) {
        myBackend.writeConfirmedConsents(confirmedConsentToExternalString(confirmed.values().stream()));
      }
      notifyConsentsUpdated();
    }
    catch (Exception e) {
      LOG.info("Unable to apply server consents", e);
    }
  }

  public @NotNull Pair<List<Consent>, Boolean> getConsents() {
    return getConsents(consent -> true);
  }

  public @NotNull Pair<List<Consent>, Boolean> getConsents(@NotNull Predicate<? super Consent> filter) {
    var allDefaults = loadDefaultConsents();
    if (isEAP()) {
      // for EA builds there is a different option for statistics sending management
      allDefaults.remove(STATISTICS_OPTION_ID);
    }
    else {
      // EAP feedback consent is relevant to EA builds only
      allDefaults.remove(lookupConsentID(EAP_FEEDBACK_OPTION_ID));
    }

    for (var it = allDefaults.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      var consent = entry.getValue().get(getDefaultLocale());
      if (consent != null && !filter.test(consent)) {
        it.remove();
      }
    }

    if (allDefaults.isEmpty()) {
      return new Pair<>(List.of(), Boolean.FALSE);
    }

    var allConfirmed = loadConfirmedConsents();
    var result = new ArrayList<Consent>();
    for (var entry : allDefaults.entrySet()) {
      var base = entry.getValue().get(getDefaultLocale());
      var localized = entry.getValue().get(getCurrentLocale());
      if (base == null) continue; 
      if (!base.isDeleted()) {
        var confirmed = allConfirmed.get(base.getId());
        var consent = localized == null || base.getVersion().isNewer(localized.getVersion()) ? base : localized;
        result.add(confirmed == null? consent : consent.derive(confirmed.isAccepted()));
      }
    }
    result.sort(Comparator.comparing(ConsentBase::getId));
    var confirmationEnabled = Boolean.parseBoolean(System.getProperty(CONSENTS_CONFIRMATION_PROPERTY, "true"));
    return new Pair<>(result, confirmationEnabled && needReconfirm(allDefaults, allConfirmed));
  }

  public void setConsents(@NotNull Collection<Consent> confirmedByUser) {
    var result = new ArrayList<ConfirmedConsent>(confirmedByUser.size());
    for (var t : confirmedByUser) {
      result.add(new ConfirmedConsent(t.getId(), t.getVersion(), t.isAccepted(), 0L));
      if (!myPluginCodes.isEmpty()) {
        var idPrefix = getProductConsentKind(myProductCode, t.getId());
        if (idPrefix != null && PER_PRODUCT_CONSENTS.contains(idPrefix)) {
          for (var pluginCode : myPluginCodes) {
            result.add(new ConfirmedConsent(idPrefix + "." + pluginCode, t.getVersion(), t.isAccepted(), 0L));
          }
        }
      }
    }
    saveConfirmedConsents(result);
  }

  private @Nullable Consent getDefaultConsent(String consentId) {
    var defaultConsents = loadDefaultConsents();
    var consentMap = defaultConsents.get(consentId);
    var defaultConsent = consentMap.get(getDefaultLocale());
    if (defaultConsent == null) return null;
    var localizedConsent = consentMap.get(getCurrentLocale());
    return localizedConsent == null || defaultConsent.getVersion().isNewer(localizedConsent.getVersion()) ?
           defaultConsent : localizedConsent;
  }

  private @Nullable ConfirmedConsent getConfirmedConsent(String consentId) {
    var defConsent = getDefaultConsent(consentId);
    if (defConsent != null && defConsent.isDeleted()) {
      return null;
    }
    return loadConfirmedConsents().get(defConsent != null ? defConsent.getId() : lookupConsentID(consentId));
  }

  private void saveConfirmedConsents(Collection<ConfirmedConsent> updates) {
    if (!updates.isEmpty()) {
      try {
        var allConfirmed = loadConfirmedConsents();
        var stamp = System.currentTimeMillis();
        for (var consent : updates) {
          consent.setAcceptanceTime(stamp);
          allConfirmed.put(consent.getId(), consent);
        }
        myBackend.writeConfirmedConsents(confirmedConsentToExternalString(allConfirmed.values().stream()));
        notifyConsentsUpdated();
      }
      catch (IOException e) {
        LOG.info("Unable to save confirmed consents", e);
      }
    }
  }

  private boolean needReconfirm(Map<String, Map<Locale, Consent>> defaults, Map<String, ConfirmedConsent> confirmed) {
    for (var consents : defaults.values()) {
      var defConsent = consents.get(getDefaultLocale());
      if (defConsent == null || defConsent.isDeleted()) {
        continue;
      }
      var confirmedConsent = confirmed.get(defConsent.getId());
      if (confirmedConsent == null) {
        return true;
      }

      var consentId = getProductConsentKind(myProductCode, defConsent.getId());
      if (consentId != null && PER_PRODUCT_CONSENTS.contains(consentId)) {
        // require confirmation if at least one of installed plugins does not have its own consent
        for (var pluginCode : myPluginCodes) {
          var pluginConfirmedConsent = confirmed.get(consentId + "." + pluginCode);
          if (pluginConfirmedConsent == null) {
            return true;
          }
        }
      }

      var confirmedVersion = confirmedConsent.getVersion();
      var defaultVersion = defConsent.getVersion();
      // for test purpose only
      if (Boolean.getBoolean(RECONFIRM_CONSENTS_PROPERTY)) {
        return true;
      }
      // consider only major version differences
      if (confirmedVersion.isOlder(defaultVersion) && confirmedVersion.getMajor() != defaultVersion.getMajor()) {
        return true;
      }
    }
    return false;
  }

  private static boolean applyServerChangesToConfirmedConsents(Map<String, ConfirmedConsent> base, Collection<ConsentAttributes> fromServer) {
    var changes = false;
    for (var update : fromServer) {
      var current = base.get(update.consentId);
      if (current != null) {
        var change = new ConfirmedConsent(update);
        if (!change.getVersion().isOlder(current.getVersion()) && current.getAcceptanceTime() < update.acceptanceTime) {
          base.put(change.getId(), change);
          changes = true;
        }
      }
    }
    return changes;
  }

  private static boolean applyServerChangesToDefaults(Map<String, Map<Locale, Consent>> base, Collection<ConsentAttributes> fromServer) {
    var changes = false;
    for (var update : fromServer) {
      var newConsent = new Consent(update);
      var current = base.get(newConsent.getId());
      if (current == null) {
        base.put(newConsent.getId(), Map.of(Locale.forLanguageTag(newConsent.getLocale()), newConsent));
        return true;
      }
      var newConsentLocale = newConsent.getLocale() != null && !newConsent.getLocale().isEmpty() ? Locale.forLanguageTag(newConsent.getLocale()) : getDefaultLocale();
      var consent = current.get(newConsentLocale);
      if (consent == null && newConsentLocale != getDefaultLocale()) {
        newConsentLocale = getDefaultLocale();
        consent = current.get(newConsentLocale);
      }
      if (consent != null && !newConsent.isDeleted() && newConsent.getVersion().isNewer(consent.getVersion())) {
        base.get(newConsent.getId()).put(newConsentLocale, newConsent);
        changes = true;
      }
    }
    return changes;
  }

  private Collection<ConsentAttributes> fromJson(@Nullable String json) {
    if (json == null || json.isEmpty()) {
      return List.of();
    }

    try {
      var data = ConsentAttributes.Companion.readListFromJson(json);
      for (var attributes : data) {
        attributes.consentId = lookupConsentID(attributes.consentId);
      }
      return data;
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return List.of();
  }

  private String consentsToJson(Stream<Consent> consents) {
    return ConsentAttributes.Companion.writeListToJson(consents.map(consent -> {
      var attributes = consent.toConsentAttributes();
      var prefix = getProductConsentKind(myProductCode, attributes.consentId);
      if (prefix != null) {
        attributes.consentId = prefix;
      }
      return attributes;
    }).toList());
  }

  private static String confirmedConsentToExternalString(Stream<ConfirmedConsent> consents) {
    return consents
      //.sorted(Comparator.comparing(ConsentBase::getId))
      .map(ConfirmedConsent::toExternalString)
      .collect(Collectors.joining(";"));
  }

  private Map<String, Map<Locale, Consent>> loadDefaultConsents() {
    var result = new HashMap<String, Map<Locale, Consent>>();
    var localizedConsentAttributes = fromJson(myBackend.readLocalizedBundledConsents());
    for (var attributes : fromJson(myBackend.readBundledConsents())) {
      var map = new HashMap<Locale, Consent>();
      map.put(getDefaultLocale(), new Consent(attributes));
      localizedConsentAttributes.stream()
        .filter(it -> Objects.equals(it.consentId, attributes.consentId))
        .findFirst()
        .ifPresent(localizedAttributes -> map.put(getCurrentLocale(), new Consent(localizedAttributes)));
      result.put(attributes.consentId, map);
    }
    try {
      applyServerChangesToDefaults(result, fromJson(myBackend.readDefaultConsents()));
    }
    catch (IOException ignored) { }
    return result;
  }

  private Map<String, ConfirmedConsent> loadConfirmedConsents() {
    var result = new HashMap<String, ConfirmedConsent>();
    try {
      var tokenizer = new StringTokenizer(myBackend.readConfirmedConsents(), ";", false);
      while (tokenizer.hasMoreTokens()) {
        var consent = ConfirmedConsent.fromString(tokenizer.nextToken());
        if (consent != null) {
          result.put(consent.getId(), consent);
        }
      }
    }
    catch (IOException ignored) { }
    return result;
  }

  private static boolean isProductConsentOfKind(String consentKind, String consentId) {
    return consentKind != null &&
           consentId.startsWith(consentKind) &&
           (consentId.length() == consentKind.length() || consentId.charAt(consentKind.length()) == '.');
  }

  @SuppressWarnings("DuplicateExpressions")
  private static String getProductConsentKind(String productCode, String consentId) {
    if (
      productCode != null &&
      consentId.endsWith(productCode) &&
      (consentId.length() == productCode.length() || consentId.charAt(consentId.length() - productCode.length() - 1) == '.')
    ) {
      return consentId.substring(0, consentId.length() - productCode.length() - 1);
    }
    return null;
  }

  private void notifyConsentsUpdated() {
    myModificationCount.incrementAndGet();
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(DataSharingSettingsChangeListener.TOPIC).consentsUpdated();
    }
  }

  @TestOnly
  public static @NotNull Path getDefaultConsentsFileForTests() {
    return getDefaultConsentsFile();
  }

  @TestOnly
  public static @NotNull Path getConfirmedConsentsFileForTests() {
    return getConfirmedConsentsFile();
  }

  public interface IOBackend {
    void writeDefaultConsents(@NotNull String data) throws IOException;
    @NotNull String readDefaultConsents() throws IOException;
    @NotNull String readBundledConsents();
    @Nullable String readLocalizedBundledConsents();
    void writeConfirmedConsents(@NotNull String data) throws IOException;
    @NotNull String readConfirmedConsents() throws IOException;
  }
}
