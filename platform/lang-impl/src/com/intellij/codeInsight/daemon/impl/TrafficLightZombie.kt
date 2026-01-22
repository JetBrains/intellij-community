// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.HighlightDisplayLevelColoredIcon
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.impl.zombie.AbstractNecromancy
import com.intellij.openapi.editor.impl.zombie.Zombie
import com.intellij.openapi.editor.markup.StatusItem
import com.intellij.openapi.editor.markup.StatusItemMetadata
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EmptyIcon
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon


internal class TrafficLightZombie(
  val icon: Icon,
  val title: String,
  val details: String,
  val showNavigation: Boolean,
  val textStatus: Boolean,
  val isToolbarEnabled: Boolean,
  val expandedStatus: List<StatusItem>,
) : Zombie {

  class Necromancy(private val project: Project) : AbstractNecromancy<TrafficLightZombie>(spellLevel=0, isDeepBury=false) {

    override fun buryZombie(grave: DataOutput, zombie: TrafficLightZombie) {
      writeString(grave, zombie.title)
      writeString(grave, zombie.details)
      writeBool(grave, zombie.showNavigation)
      writeBool(grave, zombie.textStatus)
      writeBool(grave, zombie.isToolbarEnabled)
      writeExpandedStatus(grave, zombie.expandedStatus)
      val icon = getFinalIcon2(zombie)
      writeIconNullable(grave, icon)
    }

    override fun exhumeZombie(grave: DataInput): TrafficLightZombie {
      val title:                    String = readString(grave)
      val details:                  String = readString(grave)
      val showNavigation:          Boolean = readBool(grave)
      val textStatus:              Boolean = readBool(grave)
      val isToolbarEnabled:        Boolean = readBool(grave)
      val expandedStatus: List<StatusItem> = readExpandedStatus(grave)
      val icon:                      Icon? = readIconNullable(grave)
      val finalIcon:                  Icon = getFinalIcon1(icon, expandedStatus)
      return TrafficLightZombie(
        finalIcon,
        title,
        details,
        showNavigation,
        textStatus,
        isToolbarEnabled,
        expandedStatus,
      )
    }

    // TODO: refactor this icon hell logic
    private fun getFinalIcon2(zombie: TrafficLightZombie): Icon? {
      val isIconFromExpanded = zombie.expandedStatus.any { it.icon === zombie.icon }
      return if (isIconFromExpanded) {
        null
      } else {
        zombie.icon
      }
    }

    // TODO: refactor this icon hell logic
    private fun getFinalIcon1(icon: Icon?, expandedStatus: List<StatusItem>): Icon {
      return icon ?: (expandedStatus.find { it.icon != null }?.icon ?: EmptyIcon.ICON_0)
    }

    private fun writeIconNullable(grave: DataOutput, icon: Icon?) {
      val baseIcon = if (icon is HighlightDisplayLevelColoredIcon) {
        icon.getIcon()
      } else {
        icon
      }
      writeGutterIcon(grave, baseIcon)
    }

    private fun readIconNullable(grave: DataInput): Icon? {
      return readGutterIcon(grave)
    }

    private fun writeExpandedStatus(grave: DataOutput, expandedStatus: List<StatusItem>) {
      writeInt(grave, expandedStatus.size)
      for (item in expandedStatus) {
        writeString(grave, item.text)
        writeStringNullable(grave, item.detailsText)
        val severityType = writeStatusMetadata(grave, item.metadata)
        val icon = if (severityType == SeverityType.CUSTOM) {
          item.icon
        } else {
          null
        }
        writeIconNullable(grave, icon)
      }
    }

    private fun readExpandedStatus(grave: DataInput): List<StatusItem> {
      val size = readInt(grave)
      val result = ArrayList<StatusItem>(size)
      repeat(size) {
        @Suppress("HardCodedStringLiteral")
        val text:                                 String = readString(grave)
        val detailsText:                         String? = readStringNullable(grave)
        val metadata: Pair<StatusItemMetadata, Boolean>? = readStatusMetadata(grave)
        val icon:                                  Icon? = readIconNullable(grave)
        val finalIcon:                             Icon? = getFinalIcon(icon, metadata)
        result.add(StatusItem(text, finalIcon, detailsText, metadata?.first))
      }
      return result
    }

    // TODO: refactor this icon hell logic
    private fun getFinalIcon(icon: Icon?, metadata: Pair<StatusItemMetadata, Boolean>?): Icon? {
      if (metadata != null) {
        val found = metadata.second
        if (found) {
          val severity = (metadata.first as TrafficLightStatusItemMetadata).severity
          return severityRegistrar().getRendererIconBySeverity(severity, true)
        }
      }
      return icon
    }

    private fun writeStatusMetadata(grave: DataOutput, metadata: StatusItemMetadata?): SeverityType? {
      val meta = metadata as? TrafficLightStatusItemMetadata
      var severityType: SeverityType? = null
      writeNullable(grave, meta) {
        writeInt(grave, it.count)
        severityType = writeSeverity(grave, it.severity, it.count)
      }
      return severityType
    }

    private fun readStatusMetadata(grave: DataInput): Pair<StatusItemMetadata, Boolean>? {
      return readNullable(grave) {
        val count = readInt(grave)
        val (severity, found) = readSeverity(grave)
        TrafficLightStatusItemMetadata(count, severity) to found
      }
    }

    private fun readSeverity(grave: DataInput): Pair<HighlightSeverity, Boolean> {
      val severityType = readSeverityType(grave)
      val severity = getDefaultSeverity(severityType)
      if (severity != null) {
        return severity to true
      }
      return readCustomSeverity(grave)
    }

    private fun writeSeverity(grave: DataOutput, severity: HighlightSeverity, problemCount: Int): SeverityType {
      val severityType = getSeverityType(severity)
      writeSeverityType(grave, severityType)
      if (severityType == SeverityType.CUSTOM) {
        writeCustomSeverity(grave, severity, problemCount)
      }
      return severityType
    }

    private fun writeCustomSeverity(grave: DataOutput, severity: HighlightSeverity, problemCount: Int) {
      writeString(grave, severity.name)
      writeString(grave, severity.displayName)
      writeString(grave, severity.displayCapitalizedName)
      writeString(grave, severity.getCountMessage(problemCount))
      writeInt(grave, severity.myVal)
    }

    @Suppress("HardCodedStringLiteral")
    private fun readCustomSeverity(grave: DataInput): Pair<HighlightSeverity, Boolean> {
      val name:                   String = readString(grave)
      val displayName:            String = readString(grave)
      val displayCapitalizedName: String = readString(grave)
      val countMessageTemplate:   String = readString(grave)
      val value:                     Int = readInt(grave)
      val severity = severityRegistrar().getSeverity(name)
      if (severity != null) {
        return severity to true
      }
      return HighlightSeverity(
        name,
        value,
        { displayName },
        { displayCapitalizedName },
        { countMessageTemplate },
      ) to false
    }

    private fun writeSeverityType(grave: DataOutput, severityType: SeverityType) {
      writeInt(grave, severityType.ordinal)
    }

    private fun readSeverityType(grave: DataInput): SeverityType {
      val ordinal = readInt(grave)
      return SeverityType.entries[ordinal]
    }

    private fun getSeverityType(severity: HighlightSeverity): SeverityType {
      @Suppress("DEPRECATION") // can't just ignore deprecated HighlightSeverity.INFO
      return when (severity) {
        HighlightSeverity.INFORMATION                     -> SeverityType.INFORMATION
        HighlightSeverity.TEXT_ATTRIBUTES                 -> SeverityType.TEXT_ATTRIBUTES
        HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING -> SeverityType.GENERIC_SERVER_ERROR_OR_WARNING
        HighlightSeverity.INFO                            -> SeverityType.INFO
        HighlightSeverity.WEAK_WARNING                    -> SeverityType.WEAK_WARNING
        HighlightSeverity.WARNING                         -> SeverityType.WARNING
        HighlightSeverity.ERROR                           -> SeverityType.ERROR
        else                                              -> SeverityType.CUSTOM
      }
    }

    private fun getDefaultSeverity(severityType: SeverityType): HighlightSeverity? {
      @Suppress("DEPRECATION") // can't just ignore deprecated HighlightSeverity.INFO
      return when (severityType) {
        SeverityType.INFORMATION                     -> HighlightSeverity.INFORMATION
        SeverityType.TEXT_ATTRIBUTES                 -> HighlightSeverity.TEXT_ATTRIBUTES
        SeverityType.GENERIC_SERVER_ERROR_OR_WARNING -> HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING
        SeverityType.INFO                            -> HighlightSeverity.INFO
        SeverityType.WEAK_WARNING                    -> HighlightSeverity.WEAK_WARNING
        SeverityType.WARNING                         -> HighlightSeverity.WARNING
        SeverityType.ERROR                           -> HighlightSeverity.ERROR
        SeverityType.CUSTOM                          -> null
      }
    }

    private fun severityRegistrar(): SeverityRegistrar {
      return SeverityRegistrar.getSeverityRegistrar(project)
    }

    private enum class SeverityType {
      INFORMATION,
      TEXT_ATTRIBUTES,
      GENERIC_SERVER_ERROR_OR_WARNING,
      INFO,
      WEAK_WARNING,
      WARNING,
      ERROR,
      CUSTOM,
    }
  }
}
