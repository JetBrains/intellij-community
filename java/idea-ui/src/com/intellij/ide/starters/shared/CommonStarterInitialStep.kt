// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.shared

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.local.StarterModuleBuilder
import com.intellij.ide.starters.shared.ValidationFunctions.*
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.observable.util.joinCanonicalPath
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.annotations.Nls
import java.io.File
import javax.swing.JComponent
import javax.swing.JTextField

abstract class CommonStarterInitialStep(
  protected val wizardContext: WizardContext,
  private val starterContext: CommonStarterContext,
  private val moduleBuilder: ModuleBuilder,
  protected val parentDisposable: Disposable,
  protected val starterSettings: StarterWizardSettings
) : ModuleWizardStep() {
  protected val validatedTextComponents: MutableList<JTextField> = mutableListOf()

  protected val propertyGraph: PropertyGraph = PropertyGraph()
  protected val entityNameProperty: GraphProperty<String> = propertyGraph.lazyProperty(::suggestName)
  protected val locationProperty: GraphProperty<String> = propertyGraph.lazyProperty(::suggestLocationByName)
  protected val canonicalPathProperty = locationProperty.joinCanonicalPath(entityNameProperty)
  protected val groupIdProperty: GraphProperty<String> = propertyGraph.lazyProperty { starterContext.group }
  protected val artifactIdProperty: GraphProperty<String> = propertyGraph.lazyProperty { entityName }
  protected val sdkProperty: GraphProperty<Sdk?> = propertyGraph.lazyProperty { null }

  protected val languageProperty: GraphProperty<StarterLanguage> = propertyGraph.lazyProperty { starterContext.language }
  protected val projectTypeProperty: GraphProperty<StarterProjectType> = propertyGraph.lazyProperty {
    starterContext.projectType ?: StarterProjectType("unknown", "")
  }
  protected val testFrameworkProperty: GraphProperty<StarterTestRunner> = propertyGraph.lazyProperty {
    starterContext.testFramework ?: StarterTestRunner("unknown", "")
  }
  protected val applicationTypeProperty: GraphProperty<StarterAppType> = propertyGraph.lazyProperty {
    starterContext.applicationType ?: StarterAppType("unknown", "")
  }
  protected val exampleCodeProperty: GraphProperty<Boolean> = propertyGraph.lazyProperty { starterContext.includeExamples }
  protected val gitProperty: GraphProperty<Boolean> = propertyGraph.property(false)
    .bindBooleanStorage("NewProjectWizard.gitState")

  protected var entityName: String by entityNameProperty.trim()
  protected var location: String by locationProperty
  protected var groupId: String by groupIdProperty.trim()
  protected var artifactId: String by artifactIdProperty.trim()

  protected lateinit var groupRow: Row
  protected lateinit var artifactRow: Row

  protected lateinit var sdkComboBox: JdkComboBox

  protected fun Panel.addProjectLocationUi() {
    row(UIBundle.message("label.project.wizard.new.project.name")) {
      textField()
        .bindText(entityNameProperty)
        .withSpecialValidation(listOf(CHECK_NOT_EMPTY, CHECK_SIMPLE_NAME_FORMAT),
                               createLocationWarningValidator(locationProperty))
        .columns(COLUMNS_MEDIUM)
        .focused()

      installNameGenerators(moduleBuilder.builderId, entityNameProperty)
    }.bottomGap(BottomGap.SMALL)

    val locationRow = row(UIBundle.message("label.project.wizard.new.project.location")) {
      val commentLabel = projectLocationField(locationProperty, wizardContext)
        .horizontalAlign(HorizontalAlign.FILL)
        .withSpecialValidation(CHECK_NOT_EMPTY, CHECK_LOCATION_FOR_ERROR)
        .comment(getLocationComment(), 100)
        .comment!!

      entityNameProperty.afterChange { commentLabel.text = getLocationComment() }
      locationProperty.afterChange { commentLabel.text = getLocationComment() }
    }

    if (wizardContext.isCreatingNewProject) {
      // Git should not be enabled for single module
      row(EMPTY_LABEL) {
        checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"))
          .bindSelected(gitProperty)
      }.bottomGap(BottomGap.SMALL)
    }
    else {
      locationRow.bottomGap(BottomGap.SMALL)
    }
  }

  private fun getLocationComment(): @Nls String {
    val shortPath = StringUtil.shortenPathWithEllipsis(getPresentablePath(canonicalPathProperty.get()), 60)
    return UIBundle.message("label.project.wizard.new.project.path.description", wizardContext.isCreatingNewProjectInt, shortPath)
  }

  protected fun Panel.addSampleCodeUi() {
    if (starterSettings.isExampleCodeProvided) {
      row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(exampleCodeProperty)
      }
    }
  }

  protected fun Panel.addSdkUi() {
    row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
      sdkComboBox = sdkComboBox(wizardContext, sdkProperty, StdModuleTypes.JAVA.id, moduleBuilder::isSuitableSdkType)
        .columns(COLUMNS_MEDIUM)
        .component
    }.bottomGap(BottomGap.SMALL)
  }

  protected fun Panel.addGroupArtifactUi() {
    row(JavaStartersBundle.message("title.project.group.label")) {
      groupRow = this

      textField()
        .bindText(groupIdProperty)
        .columns(COLUMNS_MEDIUM)
        .withSpecialValidation(CHECK_NOT_EMPTY, CHECK_NO_WHITESPACES, CHECK_GROUP_FORMAT, CHECK_NO_RESERVED_WORDS,
                               *getCustomValidationRules(GROUP_ID_PROPERTY))
    }.bottomGap(BottomGap.SMALL)

    row(JavaStartersBundle.message("title.project.artifact.label")) {
      artifactRow = this

      textField()
        .bindText(artifactIdProperty)
        .columns(COLUMNS_MEDIUM)
        .withSpecialValidation(CHECK_NOT_EMPTY, CHECK_NO_WHITESPACES, CHECK_ARTIFACT_SIMPLE_FORMAT, CHECK_NO_RESERVED_WORDS,
                               *getCustomValidationRules(ARTIFACT_ID_PROPERTY))
    }.bottomGap(BottomGap.SMALL)
  }

  protected open fun addFieldsBefore(layout: Panel) {}

  protected open fun addFieldsAfter(layout: Panel) {}

  protected open fun getCustomValidationRules(propertyId: String): Array<TextValidationFunction> = emptyArray()

  private fun suggestName(): String {
    return suggestName(starterContext.artifact)
  }

  protected fun suggestName(prefix: String): String {
    val projectFileDirectory = File(wizardContext.projectFileDirectory)
    return FileUtil.createSequentFileName(projectFileDirectory, prefix, "")
  }

  private fun suggestLocationByName(): String {
    return wizardContext.projectFileDirectory
  }

  private fun suggestPackageName(): String {
    return StarterModuleBuilder.suggestPackageName(groupId, artifactId)
  }

  @Suppress("SameParameterValue")
  protected fun <T : JComponent> Cell<T>.withSpecialValidation(vararg errorValidationUnits: TextValidationFunction): Cell<T> =
    withValidation(this, errorValidationUnits.asList(), null, validatedTextComponents, parentDisposable)

  protected fun <T : JComponent> Cell<T>.withSpecialValidation(
    errorValidationUnits: List<TextValidationFunction>,
    warningValidationUnit: TextValidationFunction?
  ): Cell<T> {
    return withValidation(this, errorValidationUnits, warningValidationUnit, validatedTextComponents, parentDisposable)
  }
}