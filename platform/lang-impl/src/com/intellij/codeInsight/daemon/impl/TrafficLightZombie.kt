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

  class Necromancy(
    private val project: Project,
  ) : AbstractNecromancy<TrafficLightZombie>(spellLevel=0) {

    override fun Out.writeZombie(zombie: TrafficLightZombie) {
      writeString(zombie.title)
      writeString(zombie.details)
      writeBool(zombie.showNavigation)
      writeBool(zombie.textStatus)
      writeBool(zombie.isToolbarEnabled)
      writeExpandedStatus(zombie.expandedStatus)
      val icon = getFinalIcon2(zombie)
      writeIconNullable(output, icon)
    }

    override fun In.readZombie(): TrafficLightZombie {
      val title:                    String = readString()
      val details:                  String = readString()
      val showNavigation:          Boolean = readBool()
      val textStatus:              Boolean = readBool()
      val isToolbarEnabled:        Boolean = readBool()
      val expandedStatus: List<StatusItem> = readExpandedStatus()
      val icon:                      Icon? = readIconNullable(input)
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

    private fun Out.writeExpandedStatus(expandedStatus: List<StatusItem>) {
      writeList(expandedStatus) {
        writeString(it.text)
        writeStringOrNull(it.detailsText)
        val severityType = writeStatusMetadata(it.metadata)
        val icon = if (severityType == SeverityType.CUSTOM) {
          it.icon
        } else {
          null
        }
        writeIconNullable(output, icon)
      }
    }

    private fun In.readExpandedStatus(): List<StatusItem> {
      @Suppress("HardCodedStringLiteral")
      return readList {
        val text:                                 String = readString()
        val detailsText:                         String? = readStringOrNull()
        val metadata: Pair<StatusItemMetadata, Boolean>? = readStatusMetadata()
        val icon:                                  Icon? = readIconNullable(input)
        val finalIcon:                             Icon? = getFinalIcon(icon, metadata)
        StatusItem(text, finalIcon, detailsText, metadata?.first)
      }
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

    private fun Out.writeStatusMetadata(metadata: StatusItemMetadata?): SeverityType? {
      val meta = metadata as? TrafficLightStatusItemMetadata
      var severityType: SeverityType? = null
      writeNullable(meta) {
        writeInt(it.count)
        severityType = writeSeverity(it.severity, it.count)
      }
      return severityType
    }

    private fun In.readStatusMetadata(): Pair<StatusItemMetadata, Boolean>? {
      return readNullable {
        val count = readInt()
        val (severity, found) = readSeverity()
        TrafficLightStatusItemMetadata(count, severity) to found
      }
    }

    private fun In.readSeverity(): Pair<HighlightSeverity, Boolean> {
      val severityType = readSeverityType()
      val severity = getDefaultSeverity(severityType)
      if (severity != null) {
        return severity to true
      }
      return readCustomSeverity()
    }

    private fun Out.writeSeverity(severity: HighlightSeverity, problemCount: Int): SeverityType {
      val severityType = getSeverityType(severity)
      writeSeverityType(severityType)
      if (severityType == SeverityType.CUSTOM) {
        writeCustomSeverity(severity, problemCount)
      }
      return severityType
    }

    private fun Out.writeCustomSeverity(severity: HighlightSeverity, problemCount: Int) {
      writeString(severity.name)
      writeString(severity.displayName)
      writeString(severity.displayCapitalizedName)
      writeString(severity.getCountMessage(problemCount))
      writeInt(severity.myVal)
    }

    @Suppress("HardCodedStringLiteral")
    private fun In.readCustomSeverity(): Pair<HighlightSeverity, Boolean> {
      val name:                   String = readString()
      val displayName:            String = readString()
      val displayCapitalizedName: String = readString()
      val countMessageTemplate:   String = readString()
      val value:                     Int = readInt()
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

    private fun Out.writeSeverityType(severityType: SeverityType) {
      writeInt(severityType.ordinal)
    }

    private fun In.readSeverityType(): SeverityType {
      val ordinal = readInt()
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
