// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.diagnostic.LoadingState;
import com.intellij.l10n.LocalizationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.CharsetToolkit;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final Set<String> PER_PRODUCT_CONSENTS = Set.of(EAP_FEEDBACK_OPTION_ID);
  private final BooleanSupplier myIsEap;
  private String myProductCode;
  private Set<String> myPluginCodes = Set.of();

  private final AtomicLong myModificationCount = new AtomicLong();

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }

  private static @NotNull Path getDefaultConsentsFile() {
    return PathManager.getCommonDataPath()
      .resolve(ApplicationNamesInfo.getInstance().getLowercaseProductName())
      .resolve("consentOptions/cached");
  }

  private static @NotNull Path getConfirmedConsentsFile() {
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
        Path defaultConsentsFile = getDefaultConsentsFile();
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

        for (String localizedPath : LocalizationUtil.INSTANCE.getLocalizedPaths(getBundledResourcePath(), getCurrentLocale())) {
          String loadedText = loadText(ConsentOptions.class.getClassLoader().getResourceAsStream(localizedPath));
          if (!loadedText.isEmpty()) {
            return loadedText;
          }
        }
        return null;
      }

      @Override
      public void writeConfirmedConsents(@NotNull String data) throws IOException {
        Path confirmedConsentsFile = getConfirmedConsentsFile();
        Files.createDirectories(confirmedConsentsFile.getParent());
        Files.writeString(confirmedConsentsFile, data);
        if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
          DataSharingSettingsChangeListener syncPublisher =
            ApplicationManager.getApplication().getMessageBus().syncPublisher(DataSharingSettingsChangeListener.Companion.getTOPIC());
          syncPublisher.consentWritten();
        }
      }

      @Override
      public @NotNull String readConfirmedConsents() throws IOException {
        return loadText(Files.newInputStream(getConfirmedConsentsFile()));
      }

      private static @NotNull String loadText(InputStream stream) {
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
    });

    private static @NotNull @NonNls String getBundledResourcePath() {
      if ("JetBrains".equals(System.getProperty("idea.vendor.name"))) {
        return "consents.json";
      }

      ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      return appInfo.isVendorJetBrains() ? "consents.json" : "consents-" + appInfo.getShortCompanyName() + ".json";
    }
  }

  private final IOBackend myBackend;

  ConsentOptions(IOBackend backend, boolean isEap) {
    myBackend = backend;
    myIsEap = () -> isEap;
  }

  ConsentOptions(IOBackend backend) {
    myBackend = backend;
    myIsEap = () -> {
      ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
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
    Set<String> codes = new HashSet<>();
    for (String pluginCode : pluginCodes) {
      codes.add(pluginCode.toLowerCase(getDefaultLocale()));
    }
    myPluginCodes = codes.isEmpty()? Set.of() : Collections.unmodifiableSet(codes);
  }

  public @Nullable Consent getDefaultUsageStatsConsent() {
    return getDefaultConsent(STATISTICS_OPTION_ID);
  }

  boolean isUsageStatsConsent(@NotNull Consent consent) {
    return STATISTICS_OPTION_ID.equals(consent.getId());
  }

  public static @NotNull Predicate<Consent> condUsageStatsConsent() {
    return consent -> STATISTICS_OPTION_ID.equals(consent.getId());
  }

  public static @NotNull Predicate<Consent> condEAPFeedbackConsent() {
    return consent -> isProductConsentOfKind(EAP_FEEDBACK_OPTION_ID, consent.getId());
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

  private @NotNull Permission getPermission(final String consentId) {
    final ConfirmedConsent confirmedConsent = getConfirmedConsent(consentId);
    return confirmedConsent == null? Permission.UNDEFINED : confirmedConsent.isAccepted()? Permission.YES : Permission.NO;
  }

  private boolean setPermission(final String consentId, boolean allowed) {
    final Consent defConsent = getDefaultConsent(consentId);
    if (defConsent != null && !defConsent.isDeleted()) {
      setConsents(Collections.singleton(defConsent.derive(allowed)));
      return true;
    }
    return false;
  }

  private String lookupConsentID(String consentId) {
    final String productCode = myProductCode;
    return productCode != null && PER_PRODUCT_CONSENTS.contains(consentId)? consentId + "." + productCode : consentId;
  }

  public @Nullable String getConfirmedConsentsString() {
    final Map<String, Map<Locale,Consent>> defaults = loadDefaultConsents();
    if (!defaults.isEmpty()) {
      final String str = confirmedConsentToExternalString(
        loadConfirmedConsents().values().stream().filter(c -> {
          Map<Locale, Consent> defaultConsents = defaults.get(c.getId());
          final Consent def = defaultConsents != null? defaultConsents.get(getDefaultLocale()): null;
          if (def != null) {
            return !def.isDeleted();
          }
          for (String prefix : PER_PRODUCT_CONSENTS) {
            // allow also JB plugin consents, which do not have corresponding 'direct' defaults
            if (isProductConsentOfKind(prefix, c.getId())) {
              return true;
            }
          }
          return false;
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
      final @NotNull Map<String, Map<Locale, Consent>> defaults = loadDefaultConsents();
      if (applyServerChangesToDefaults(defaults, fromServer)) {
        myBackend.writeDefaultConsents(consentsToJson(defaults.values().stream().flatMap(it -> it.values().stream())));
      }
      // confirmed consents
      final Map<String, ConfirmedConsent> confirmed = loadConfirmedConsents();
      if (applyServerChangesToConfirmedConsents(confirmed, fromServer)) {
        myBackend.writeConfirmedConsents(confirmedConsentToExternalString(confirmed.values().stream()));
      }
      myModificationCount.incrementAndGet();
    }
    catch (Exception e) {
      LOG.info("Unable to apply server consents", e);
    }
  }

  public @NotNull Pair<List<Consent>, Boolean> getConsents() {
    return getConsents(consent -> true);
  }

  public @NotNull Pair<List<Consent>, Boolean> getConsents(@NotNull Predicate<? super Consent> filter) {
    final Map<String, Map<Locale, Consent>> allDefaults = loadDefaultConsents();
    if (isEAP()) {
      // for EA builds there is a different option for statistics sending management
      allDefaults.remove(STATISTICS_OPTION_ID);
    }
    else {
      // EAP feedback consent is relevant to EA builds only
      allDefaults.remove(lookupConsentID(EAP_FEEDBACK_OPTION_ID));
    }

    for (Iterator<Map.Entry<String, Map<Locale, Consent>>> it = allDefaults.entrySet().iterator(); it.hasNext(); ) {
      final Map.Entry<String, Map<Locale, Consent>> entry = it.next();
      if (!filter.test(entry.getValue().get(getDefaultLocale()))) {
        it.remove();
      }
    }

    if (allDefaults.isEmpty()) {
      return new Pair<>(Collections.emptyList(), Boolean.FALSE);
    }

    final Map<String, ConfirmedConsent> allConfirmed = loadConfirmedConsents();
    final List<Consent> result = new ArrayList<>();
    for (Map.Entry<String, Map<Locale, Consent>> entry : allDefaults.entrySet()) {
      final Consent base = entry.getValue().get(getDefaultLocale());
      final Consent localized = entry.getValue().get(getCurrentLocale());
      if (!base.isDeleted()) {
        final ConfirmedConsent confirmed = allConfirmed.get(base.getId());
        Consent consent = localized == null || base.getVersion().isNewer(localized.getVersion()) ? base : localized;
        result.add(confirmed == null? consent : consent.derive(confirmed.isAccepted()));
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

        if (!myPluginCodes.isEmpty()) {
          final String idPrefix = getProductConsentKind(myProductCode, t.getId());
          if (idPrefix != null && PER_PRODUCT_CONSENTS.contains(idPrefix)) {
            for (String pluginCode : myPluginCodes) {
              list.add(new ConfirmedConsent(idPrefix + "." + pluginCode, t.getVersion(), t.isAccepted(), 0L));
            }
          }
        }

      }
      result = list;
    }
    saveConfirmedConsents(result);
  }

  private @Nullable Consent getDefaultConsent(String consentId) {
    Map<String, Map<Locale, Consent>> defaultConsents = loadDefaultConsents();
    Map<Locale, Consent> consentMap = defaultConsents.get(consentId);
    Consent defaultConsent = consentMap.get(getDefaultLocale());
    Consent localizedConsent = consentMap.get(getCurrentLocale());
    return localizedConsent == null || defaultConsent.getVersion().isNewer(localizedConsent.getVersion())
           ? defaultConsent
           : localizedConsent;
  }

  private @Nullable ConfirmedConsent getConfirmedConsent(String consentId) {
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
        myModificationCount.incrementAndGet();
      }
      catch (IOException e) {
        LOG.info("Unable to save confirmed consents", e);
      }
    }
  }

  public boolean needsReconfirm(Consent consent) {
    if (consent == null || consent.isDeleted() || isEAP() && STATISTICS_OPTION_ID.equals(consent.getId())) {
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

  private boolean needReconfirm(Map<String, Map<Locale, Consent>> defaults, Map<String, ConfirmedConsent> confirmed) {
    for (Map<Locale, Consent> consents : defaults.values()) {
      Consent defConsent = consents.get(getDefaultLocale());
      if (defConsent.isDeleted()) {
        continue;
      }
      final ConfirmedConsent confirmedConsent = confirmed.get(defConsent.getId());
      if (confirmedConsent == null) {
        return true;
      }

      final String consentId = getProductConsentKind(myProductCode, defConsent.getId());
      if (consentId != null && PER_PRODUCT_CONSENTS.contains(consentId)) {
        // require confirmation if at least one of installed plugins does not have its own consent
        for (String pluginCode : myPluginCodes) {
          final ConfirmedConsent pluginConfirmedConsent = confirmed.get(consentId + "." + pluginCode);
          if (pluginConfirmedConsent == null) {
            return true;
          }
        }
      }

      final Version confirmedVersion = confirmedConsent.getVersion();
      final Version defaultVersion = defConsent.getVersion();
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

  private static boolean applyServerChangesToDefaults(@NotNull Map<String, Map<Locale, Consent>> base, @NotNull Collection<ConsentAttributes> fromServer) {
    boolean changes = false;
    for (ConsentAttributes update : fromServer) {
      final Consent newConsent = new Consent(update);
      final Map<Locale, Consent> current = base.get(newConsent.getId());
      if (current == null) {
        base.put(newConsent.getId(), Map.of(Locale.forLanguageTag(newConsent.getLocale()), newConsent));
        return true;
      }
      Locale newConsentLocale = newConsent.getLocale() != null && !newConsent.getLocale().isEmpty()? Locale.forLanguageTag(newConsent.getLocale()): getDefaultLocale();
      Consent consent = current.get(newConsentLocale);
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

  private @NotNull Collection<ConsentAttributes> fromJson(@Nullable String json) {
    if (json == null || json.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      List<ConsentAttributes> data = ConsentAttributes.Companion.readListFromJson(json);
      for (ConsentAttributes attributes : data) {
        attributes.consentId = lookupConsentID(attributes.consentId);
      }
      return data;
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return Collections.emptyList();
  }

  private @NotNull String consentsToJson(@NotNull Stream<Consent> consents) {
    return ConsentAttributes.Companion.writeListToJson(consents.map(consent -> {
      final ConsentAttributes attribs = consent.toConsentAttributes();
      final String prefix = getProductConsentKind(myProductCode, attribs.consentId);
      if (prefix != null) {
        attribs.consentId = prefix;
      }
      return attribs;
    }).toList());
  }

  private static @NotNull String confirmedConsentToExternalString(@NotNull Stream<ConfirmedConsent> consents) {
    return consents/*.sorted(Comparator.comparing(confirmedConsent -> confirmedConsent.getId()))*/.map(ConfirmedConsent::toExternalString).collect(Collectors.joining(";"));
  }

  private @NotNull Map<String, Map<Locale, Consent>> loadDefaultConsents() {
    final Map<String, Map<Locale, Consent>> result = new HashMap<>();
    Collection<ConsentAttributes> localizedConsentAttributes = fromJson(myBackend.readLocalizedBundledConsents());
    for (ConsentAttributes attributes : fromJson(myBackend.readBundledConsents())) {
      HashMap<Locale, Consent> map = new HashMap<>();
      map.put(getDefaultLocale(), new Consent(attributes));
      localizedConsentAttributes.stream().filter(it -> Objects.equals(it.consentId, attributes.consentId)).findFirst()
        .ifPresent(localizedAttributes -> map.put(getCurrentLocale(), new Consent(localizedAttributes)));
      result.put(attributes.consentId, map);
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

  private static boolean isProductConsentOfKind(final String consentKind, String consentId) {
    return consentKind != null && consentId.startsWith(consentKind) && (consentId.length() == consentKind.length() || consentId.charAt(consentKind.length()) == '.');
  }

  private static String getProductConsentKind(final String productCode, String consentId) {
    if (productCode != null && consentId.endsWith(productCode) && (consentId.length() == productCode.length() || consentId.charAt(consentId.length() - productCode.length() - 1) == '.')) {
      return consentId.substring(0, consentId.length() - productCode.length() - 1);
    }
    return null;
  }

  protected interface IOBackend {
    void writeDefaultConsents(@NotNull String data) throws IOException;
    @NotNull
    String readDefaultConsents() throws IOException;
    @NotNull
    String readBundledConsents();
    @Nullable
    String readLocalizedBundledConsents();
    void writeConfirmedConsents(@NotNull String data) throws IOException;
    @NotNull
    String readConfirmedConsents() throws IOException;
  }
}