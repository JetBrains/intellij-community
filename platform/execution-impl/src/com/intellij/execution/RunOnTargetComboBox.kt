// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentWizard
import com.intellij.execution.target.getTargetType
import com.intellij.execution.target.local.LocalTargetType
import com.intellij.execution.ui.InvalidRunConfigurationIcon
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.ListCellRenderer


@ApiStatus.Internal
sealed class Item(val displayName: @NlsContexts.Label String, open val icon: Icon?, val separator: @NlsContexts.Separator String? = null)

@ApiStatus.Internal
abstract class Target(
  displayName: @NlsContexts.Label String,
  icon: Icon,
  val targetName: String,
  separator: @NlsContexts.Separator String? = null,
) : Item(displayName, icon, separator)

@ApiStatus.Internal
class SavedTarget(private val config: TargetEnvironmentConfiguration) :
  Target(config.displayName, config.getTargetType().icon, config.displayName) {

  var validationInfo: ValidationInfo? = null
    private set

  init {
    revalidateConfiguration()
  }

  fun revalidateConfiguration() {
    try {
      config.validateConfiguration()
      validationInfo = null
    }
    catch (e: RuntimeConfigurationException) {
      @Suppress("HardCodedStringLiteral")
      validationInfo = ValidationInfo(e.localizedMessage)
    }
  }

  fun hasErrors(): Boolean {
    return this.validationInfo != null
  }

  override val icon: Icon?
    get() {
      val rawIcon = super.icon
      return if (rawIcon != null && hasErrors()) InvalidRunConfigurationIcon(rawIcon) else rawIcon
    }
}

/**
 * Represents the "local machine" target.
 */
@ApiStatus.Internal
class LocalTarget(separator: @NlsContexts.Separator String? = null) :
  Target(ExecutionBundle.message("local.machine"), AllIcons.Nodes.HomeFolder, LocalTargetType.LOCAL_TARGET_NAME, separator)

@ApiStatus.Internal
class Type<T : TargetEnvironmentConfiguration>(private val type: TargetEnvironmentType<T>, separator: @NlsContexts.Separator String?) :
  Item(ExecutionBundle.message("run.on.targets.label.new.target.of.type", type.displayName), type.icon, separator) {

  fun createWizard(project: Project, languageRuntime: LanguageRuntimeType<*>?): TargetEnvironmentWizard? {
    return TargetEnvironmentWizard.createWizard(project, type, languageRuntime)
  }
}

/**
 * We render the project default target item as "Local Machine" without "Project Default" prefix if we do not have any saved targets in
 * the list.
 *
 * We cannot use the size of the model explicitly in {@code customizeCellRenderer(...)} method to determine whether there are
 * "Saved targets" items because the model also contains "New Target" section with the corresponding items.
 */
internal fun createItemRenderer(
  hasSavedTargetsSupplier: Supplier<Boolean>,
  projectDefaultTarget: TargetEnvironmentConfiguration?,
): ListCellRenderer<Item?> {
  val projectDefaultTargetItem = if (projectDefaultTarget != null) SavedTarget(projectDefaultTarget) else null

  return listCellRenderer {
    value?.separator?.let {
      separator {
        text = it
      }
    }

    val value = value
    if (value != null) {
      value.icon?.let { icon(it) }
      text(value.displayName)
      return@listCellRenderer
    }

    when {
      !hasSavedTargetsSupplier.get() -> {
        icon(AllIcons.Nodes.HomeFolder)
        text(ExecutionBundle.message("local.machine"))
      }

      projectDefaultTargetItem == null -> {
        icon(AllIcons.Nodes.HomeFolder)
        text(ExecutionBundle.message("targets.details.project.default"))
        text(ExecutionBundle.message("local.machine")) {
          foreground = greyForeground
        }
      }

      else -> {
        projectDefaultTargetItem.icon?.let { icon(it) }
        text(ExecutionBundle.message("targets.details.project.default"))
        text(projectDefaultTargetItem.displayName) {
          foreground = greyForeground
        }
      }
    }
  }
}
