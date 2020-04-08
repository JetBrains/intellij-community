// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.configurationStore.BundledSchemeEP
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileLoadUtil
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileProcessor
import com.intellij.serviceContainer.NonInjectable
import org.jdom.JDOMException
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.Function

open class ApplicationInspectionProfileManagerBase @TestOnly @NonInjectable constructor(schemeManagerFactory: SchemeManagerFactory) : BaseInspectionProfileManager(
  ApplicationManager.getApplication().messageBus), InspectionProfileManager {
  override val schemeManager = schemeManagerFactory.create(InspectionProfileManager.INSPECTION_DIR, object : InspectionProfileProcessor() {
    override fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String) = fileNameWithoutExtension

    override fun createScheme(dataHolder: SchemeDataHolder<InspectionProfileImpl>,
                              name: String,
                              attributeProvider: Function<in String, String?>,
                              isBundled: Boolean): InspectionProfileImpl {
      return InspectionProfileImpl(name,
                                   InspectionToolRegistrar.getInstance(),
                                   this@ApplicationInspectionProfileManagerBase,
                                   dataHolder)
    }

    override fun onSchemeAdded(scheme: InspectionProfileImpl) {
      fireProfileChanged(scheme)
    }
  })
  protected val profilesAreInitialized = AtomicNotNullLazyValue.createValue {
    val app = ApplicationManager.getApplication()
    if (!(app.isUnitTestMode || app.isHeadlessEnvironment)) {
      for (ep in BUNDLED_EP_NAME.iterable) {
        schemeManager.loadBundledScheme(ep.path!! + ".xml", ep)
      }
    }
    schemeManager.loadSchemes()

    if (schemeManager.isEmpty) {
      schemeManager.addScheme(InspectionProfileImpl(
        DEFAULT_PROFILE_NAME,
        InspectionToolRegistrar.getInstance(), this))
    }
  }

  @Volatile
  protected var LOAD_PROFILES = !ApplicationManager.getApplication().isUnitTestMode
  override fun getProfiles(): Collection<InspectionProfileImpl> {
    initProfiles()
    return Collections.unmodifiableList(schemeManager.allSchemes)
  }

  fun initProfiles() {
    if (LOAD_PROFILES) {
      profilesAreInitialized.value
    }
  }

  @Throws(JDOMException::class, IOException::class)
  open fun loadProfile(path: String): InspectionProfileImpl? {
    val file = Paths.get(path)
    if (Files.isRegularFile(file)) {
      return InspectionProfileLoadUtil.load(file, InspectionToolRegistrar.getInstance(), this)
    }
    return getProfile(path, false)
  }

  override fun setRootProfile(profileName: String?) {
    schemeManager.currentSchemeName = profileName
  }

  override fun getProfile(name: String, returnRootProfileIfNamedIsAbsent: Boolean): InspectionProfileImpl? {
    val found = schemeManager.findSchemeByName(name)
    if (found != null) {
      return found
    }

    // profile was deleted
    return if (returnRootProfileIfNamedIsAbsent) currentProfile else null
  }

  override fun getCurrentProfile(): InspectionProfileImpl {
    initProfiles()

    val current = schemeManager.activeScheme
    if (current != null) {
      return current
    }

    // use default as base, not random custom profile
    val result = schemeManager.findSchemeByName(DEFAULT_PROFILE_NAME)
    if (result == null) {
      val profile = InspectionProfileImpl(
        DEFAULT_PROFILE_NAME)
      addProfile(profile)
      return profile
    }
    return result
  }

  override fun fireProfileChanged(profile: InspectionProfileImpl) {
  }

  companion object {
    private val BUNDLED_EP_NAME = ExtensionPointName<BundledSchemeEP>("com.intellij.bundledInspectionProfile")


      @JvmStatic
      fun getInstanceBase() = service<InspectionProfileManager>() as ApplicationInspectionProfileManagerBase
  }
}
