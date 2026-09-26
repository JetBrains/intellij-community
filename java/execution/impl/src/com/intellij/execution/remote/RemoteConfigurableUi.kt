// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.remote

import com.intellij.application.options.ModuleDescriptionsComboBox
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.COLUMNS_TINY
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selectedValueIs
import com.intellij.util.ui.JBDimension
import org.jetbrains.annotations.Nls
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JLabel
import javax.swing.JTextArea

internal class RemoteConfigurableUi {

  lateinit var modeCombo: ComboBox<Mode>
  lateinit var autoRestart: JBCheckBox
  lateinit var transportCombo: ComboBox<Transport>
  lateinit var hostName: JBTextField
  lateinit var port: JBTextField
  lateinit var address: JBTextField
  lateinit var argsArea: JTextArea
  lateinit var jdkVersionLink: DropDownLink<JdkVersionItem>
  lateinit var moduleCombo: ModuleDescriptionsComboBox

  @JvmField
  val panel = panel {
    twoColumns(
      {
        row(JavaCompilerBundle.message("label.debugger.mode")) {
          modeCombo = comboBox(Mode.entries, textListCellRenderer("") { it.displayName })
            .align(AlignX.RIGHT)
            .columns(COLUMNS_SHORT)
            .component
        }
      }) {
      autoRestart = checkBox(ExecutionBundle.message("auto.restart"))
        .visibleIf(modeCombo.selectedValueIs(Mode.LISTEN))
        .component
    }

    twoColumns(
      {
        row(JavaCompilerBundle.message("label.transport")) {
          transportCombo = comboBox(Transport.entries, textListCellRenderer("") { it.displayName })
            .align(AlignX.RIGHT)
            .columns(COLUMNS_SHORT)
            .component
        }
      }
    ).visible(SystemInfo.isWindows)

    val socket = if (SystemInfo.isWindows) transportCombo.selectedValueIs(Transport.SOCKET) else ComponentPredicate.TRUE

    twoColumns(
      {
        row(JavaCompilerBundle.message("label.host")) {
          hostName = textField()
            .align(AlignX.FILL)
            .component
        }
      }) {
      port = textField()
        .label(JavaCompilerBundle.message("label.port"))
        .columns(COLUMNS_TINY)
        .component
    }.visibleIf(socket)

    twoColumns(
      {
        row(JavaCompilerBundle.message("label.address")) {
          address = textField()
            .align(AlignX.FILL)
            .component
        }
      }).visibleIf(!socket)

    lateinit var argsLabel: JLabel
    row {
      argsLabel = label(ExecutionBundle.message("command.line.arguments.for.remote.jvm"))
        .resizableColumn()
        .component
      jdkVersionLink = dropDownLink(JdkVersionItem.JDK9, JdkVersionItem.entries)
        .applyToComponent { toolTipText = ExecutionBundle.message("jvm.arguments.format") }
        .component
    }.topGap(TopGap.SMALL)

    row {
      argsArea = cell(JTextArea()) // Without scrollbar
        .comment(ExecutionBundle.message("copy.and.paste.the.arguments.to.the.command.line.when.jvm.is.started"))
        .rows(2)
        .align(AlignX.FILL)
        .applyToComponent {
          argsLabel.labelFor = this
          putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false))
          border = SideBorder(JBColor.border(), SideBorder.ALL)
          lineWrap = true
          wrapStyleWord = true
          isEditable = false
          minimumSize = JBDimension(100, 0)
          addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
              selectAll()
            }
          })
        }
        .component
    }.bottomGap(BottomGap.SMALL)

    row(ExecutionBundle.message("use.module.classpath")) {
      moduleCombo = cell(ModuleDescriptionsComboBox())
        .comment(ExecutionBundle.message("first.search.for.sources.of.the.debugged.classes"),
                 maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
        .align(AlignX.FILL)
        .applyToComponent { allowEmptySelection(JavaCompilerBundle.message("whole.project")) }
        .component
    }.layout(RowLayout.INDEPENDENT)
  }

  internal enum class Mode(val displayName: @Nls String) {
    ATTACH(ExecutionBundle.message("combo.attach.to.remote")),
    LISTEN(ExecutionBundle.message("combo.listen.to.remote"))
  }

  internal enum class Transport(val displayName: String) {
    SOCKET("Socket"),
    SHMEM("Shared memory")
  }

  internal enum class JdkVersionItem(val version: JavaSdkVersion, val displayName: @Nls String) {

    JDK9(JavaSdkVersion.JDK_1_9, ExecutionBundle.message("combo.java.version.9+")) {
      override fun getLaunchCommandLine(connection: RemoteConnection): String {
        val commandLine = JDK5to8.getLaunchCommandLine(connection)
        if (connection.isUseSockets && !connection.isServerMode) {
          val address = connection.applicationAddress
          return commandLine.replace("address=$address", "address=*:$address")
        }
        return commandLine
      }
    },

    JDK5to8(JavaSdkVersion.JDK_1_5, ExecutionBundle.message("combo.java.version.5.to.8")) {
      override fun getLaunchCommandLine(connection: RemoteConnection): String {
        return connection.launchCommandLine
          .replace("-Xdebug", "")
          .replace("-Xrunjdwp:", "-agentlib:jdwp=")
          .trim()
      }
    },

    JDK1_4(JavaSdkVersion.JDK_1_4, ExecutionBundle.message("combo.java.version.1.4")) {
      override fun getLaunchCommandLine(connection: RemoteConnection): String = connection.launchCommandLine
    },

    JDK1_3(JavaSdkVersion.JDK_1_3, ExecutionBundle.message("combo.java.version.1.3")) {
      override fun getLaunchCommandLine(connection: RemoteConnection): String {
        return "-Xnoagent -Djava.compiler=NONE " + connection.launchCommandLine
      }
    };

    abstract fun getLaunchCommandLine(connection: RemoteConnection): String

    /**
     * [DropDownLink] shows the item text through [toString].
     */
    override fun toString(): @Nls String = displayName
  }
}

private fun Panel.twoColumns(column1: (Panel.() -> Unit), column2: (Row.() -> Unit)? = null): Row {
  return row {
    panel {
      column1()
    }.align(AlignX.FILL)
    if (column2 == null) {
      cell()
    }
    else {
      column2()
    }
  }.layout(RowLayout.PARENT_GRID)
}
