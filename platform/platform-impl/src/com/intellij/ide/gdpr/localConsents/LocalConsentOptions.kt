// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.localConsents

import com.intellij.ide.gdpr.ConfirmedConsent
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentAttributes
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.Version
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate

private const val LOCAL_CONSENT_JSON = "localConsents.json"
private const val TRACE_DATA_COLLECTION_NON_COM_OPTION_ID = "ai.trace.data.collection.and.use.noncom.policy"
private const val TRACE_DATA_COLLECTION_COM_OPTION_ID = "ai.trace.data.collection.and.use.com.policy"

@ApiStatus.Internal
object LocalConsentOptions {
  private val LOG = Logger.getInstance(LocalConsentOptions::class.java)

  private val backend: ConsentOptions.IOBackend by lazy {
    object : ConsentOptions.IOBackendImpl(LOCAL_CONSENT_JSON, getConfirmedLocalConsentsFile()) {
      override fun writeDefaultConsents(data: String) {
        throw UnsupportedOperationException("There are no default local consents")
      }

      override fun readDefaultConsents(): String {
        throw UnsupportedOperationException("There are no default local consents")
      }
    }
  }

  fun condTraceDataCollectionNonComLocalConsent(): Predicate<Consent> {
    return Predicate { localConsent: Consent -> TRACE_DATA_COLLECTION_NON_COM_OPTION_ID == localConsent.id }
  }

  fun condTraceDataCollectionComLocalConsent(): Predicate<Consent> {
    return Predicate { localConsent: Consent -> TRACE_DATA_COLLECTION_COM_OPTION_ID == localConsent.id }
  }

  fun getTraceDataCollectionNonComPermission(): ConsentOptions.Permission {
    return getPermission(TRACE_DATA_COLLECTION_NON_COM_OPTION_ID)
  }

  fun setTraceDataCollectionNonComPermission(permitted: Boolean) {
    setPermission(TRACE_DATA_COLLECTION_NON_COM_OPTION_ID, permitted)
  }

  fun getTraceDataCollectionComPermission(): ConsentOptions.Permission {
    return getPermission(TRACE_DATA_COLLECTION_COM_OPTION_ID)
  }

  fun setTraceDataCollectionComPermission(permitted: Boolean) {
    setPermission(TRACE_DATA_COLLECTION_COM_OPTION_ID, permitted)
  }

  fun getLocalConsents(): List<Consent> {
    val confirmed = loadConfirmedLocalConsents()
    val result = mutableListOf<Consent>()
    for ((_, localeMap) in loadBundledLocalConsents()) {
      val base = localeMap[ConsentOptions.getDefaultLocale()]
      val localized = localeMap[ConsentOptions.getCurrentLocale()]
      if (base != null) {
        val confirmedLocalConsent = confirmed[base.id]
        val localConsent = localized ?: base
        result.add(if (confirmedLocalConsent == null) localConsent else localConsent.derive(confirmedLocalConsent.isAccepted))
      }
    }

    result.sortBy { it.id }
    return result
  }

  fun setLocalConsents(confirmedByUser: List<Consent>) {
    if (confirmedByUser.isEmpty()) {
      return
    }

    try {
      val allAccepted = loadConfirmedLocalConsents().toMutableMap()
      val acceptanceTime = System.currentTimeMillis()
      for (consent in confirmedByUser) {
        allAccepted[consent.id] = ConfirmedConsent(consent.id, Version.UNKNOWN, consent.isAccepted, acceptanceTime)
      }
      backend.writeConfirmedConsents(confirmedLocalConsentsToExternalString(allAccepted.values))
      ConsentOptions.updateConsentListeners()
    }
    catch (e: Exception) {
      LOG.info("Unable to save local consents", e)
    }
  }

  private fun getPermission(localConsentId: String): ConsentOptions.Permission {
    val confirmedLocalConsent = getConfirmedLocalConsent(localConsentId)
    return if (confirmedLocalConsent == null)
      ConsentOptions.Permission.UNDEFINED
    else if (confirmedLocalConsent.isAccepted)
      ConsentOptions.Permission.YES
    else ConsentOptions.Permission.NO
  }

  private fun setPermission(localConsentId: String, allowed: Boolean): Boolean {
    val defLocalConsent: Consent? = getDefaultLocalConsent(localConsentId)
    if (defLocalConsent != null) {
      setLocalConsents(listOf(defLocalConsent.derive(allowed)))
      return true
    }
    return false
  }

  private fun getConfirmedLocalConsent(localConsentId: String): ConfirmedConsent? {
    return loadConfirmedLocalConsents()[localConsentId]
  }

  private fun getDefaultLocalConsent(localConsentId: String): Consent? {
    val defaultLocalConsents: Map<String, Map<Locale, Consent>> = loadBundledLocalConsents()
    val localConsentMap: Map<Locale, Consent> = defaultLocalConsents[localConsentId] ?: return null
    return localConsentMap[ConsentOptions.getDefaultLocale()] ?: localConsentMap[ConsentOptions.getCurrentLocale()]
  }

  private fun getConfirmedLocalConsentsFile(): Path {
    return PathManager.getCommonDataPath()
      .resolve(ApplicationNamesInfo.getInstance().lowercaseProductName)
      .resolve("localConsents/accepted")
  }

  private fun confirmedLocalConsentsToExternalString(consents: Collection<ConfirmedConsent>): String =
    consents.joinToString(";", transform = ConfirmedConsent::toExternalString)

  private fun fromJson(json: String?): List<ConsentAttributes> {
    if (json.isNullOrEmpty()) {
      return emptyList()
    }

    return try {
      ConsentAttributes.readListFromJson(json)
    }
    catch (e: Throwable) {
      LOG.info(e)
      emptyList()
    }
  }

  private fun loadBundledLocalConsents(): Map<String, Map<Locale, Consent>> {
    val result = mutableMapOf<String, MutableMap<Locale, Consent>>()
    val localizedAttributes = fromJson(backend.readLocalizedBundledConsents())
    for (attributes in fromJson(backend.readBundledConsents())) {
      val attributesId = attributes.consentId ?: continue
      val map = mutableMapOf<Locale, Consent>()
      map[ConsentOptions.getDefaultLocale()] = Consent(attributes)

      localizedAttributes
        .firstOrNull { localizedAttributes -> localizedAttributes.consentId == attributesId }
        ?.let { localizedAttr ->
          map[ConsentOptions.getCurrentLocale()] = Consent(localizedAttr)
        }

      result[attributesId] = map
    }
    return result
  }

  private fun loadConfirmedLocalConsents(): Map<String, ConfirmedConsent> {
    val result = mutableMapOf<String, ConfirmedConsent>()
    try {
      val tokenizer = StringTokenizer(backend.readConfirmedConsents(), ";", false)
      while (tokenizer.hasMoreTokens()) {
        val confirmedLocalConsent = ConfirmedConsent.fromString(tokenizer.nextToken())
        if (confirmedLocalConsent != null) {
          result[confirmedLocalConsent.id] = confirmedLocalConsent
        }
      }
    }
    catch (_: IOException) {
    }
    return result
  }
}
