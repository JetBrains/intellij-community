// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.ide.customize.transferSettings.models.SettingsPreferences
import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.TransferSettingsIdeRepresentationListener
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ActiveComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.util.EventListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

abstract class IdeRepresentationSection(private val prefs: SettingsPreferences,
                                        final override val key: SettingsPreferencesKind,
                                        private val icon: Icon) : TransferSettingsSection {
  companion object {
    @JvmStatic
    protected val LIMIT = 3
  }
  protected val _isSelected: AtomicBooleanProperty = AtomicBooleanProperty(prefs[key])
  protected val _isEnabled = AtomicBooleanProperty(true)
  protected open val disabledCheckboxText: String? = null
  private val leftGap = 20
  private var morePanelFactory: ((AtomicBooleanProperty) -> JComponent)? = null
  private var moreLabel: String = "More.."

  val isSelected: Boolean by _isSelected

  private val listeners = EventDispatcher.create(TransferSettingsIdeRepresentationListener::class.java)

  init {
    _isSelected.afterChange {
      prefs[key] = it
      listeners.multicaster.action()
    }
    _isEnabled.afterChange {
      listeners.multicaster.action()
    }
  }

  override fun onStateUpdate(action: TransferSettingsIdeRepresentationListener) {
    listeners.addListener(action)
  }

  protected abstract fun getContent(): JComponent

  final override fun block() {
    _isEnabled.set(false)
  }

  final override fun getUI(): DialogPanel = panel {
    customizeSpacingConfiguration(EmptySpacingConfiguration()) {
      row {
        icon(icon).align(AlignY.TOP).customize(UnscaledGaps(left = 30, right = 30)).applyToComponent {
          _isSelected.afterChange {
            this.icon = if (it) this@IdeRepresentationSection.icon else IconLoader.getDisabledIcon(icon)
          }
          _isEnabled.afterChange {
            this.icon = if (it && _isSelected.get()) this@IdeRepresentationSection.icon else IconLoader.getDisabledIcon(icon)
          }
          border = JBUI.Borders.empty()
        }

        panel {
          row {
            checkBox(name).bold().bindSelected(_isSelected).applyToComponent {
              _isSelected.afterChange {
                this.foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
              }
              _isEnabled.afterChange {
                this.foreground = if (it || _isSelected.get()) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
                isEnabled = it
              }
            }.customize(UnscaledGaps(bottom = 10, top = 5))
            label("").visible(false).apply {
              applyToComponent { foreground = UIUtil.getLabelDisabledForeground() }
              _isSelected.afterChange {
                visible(!it)
                if (disabledCheckboxText != null && !it) {
                  component.text = disabledCheckboxText
                }
              }
            }.customize(UnscaledGaps(left = 10))
          }.layout(RowLayout.INDEPENDENT)

          row {
            cell(getContent()).customize(UnscaledGaps(left = leftGap, bottom = 5)) // TODO: retrieve size of checkbox and add padding here
          }

          val mp = morePanelFactory
          val ml = moreLabel
          if (mp != null) {
            row {
              link(ml) {
                val l = it.source as JComponent
                val mpinst = mp(_isSelected)
                if (mpinst.border == null) {
                  mpinst.border = JBUI.Borders.empty(0, 5)
                }

                mpinst.preferredSize = JBUI.size(Dimension(250, getPopupHeight()))

                val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(mpinst, mpinst).apply {
                  setCancelOnClickOutside(true)
                  setCancelOnWindowDeactivation(true)
                  setMovable(false)
                  setResizable(false)
                  setRequestFocus(true)
                  setFocusable(true)
                  setLocateByContent(true)
                  setMayBeParent(false)
                  setStretchToOwnerHeight(false)
                }.createPopup()

                popup.show(RelativePoint(l, Point(JBUIScale.scale(2), l.height)))


              }.customize(UnscaledGaps(left = leftGap, bottom = 5, top = 5))
            }
          }
        }.align(AlignY.TOP)
      }
    }
  }

  protected open fun getPopupHeight() = 150

    protected fun Row.mutableLabel(@NlsContexts.Label text: String) = text(text).applyToComponent {
    _isSelected.afterChange {
      this.foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
    }
    _isEnabled.afterChange {
      this.foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
    }
  }

  protected fun mutableJLabel(@NlsContexts.Label text: String): JLabel = JLabel(text).apply {
    _isSelected.afterChange {
      this.foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
    }
    _isEnabled.afterChange {
      this.foreground = if (it || !_isSelected.get()) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
    }
  }

  protected fun withMoreLabel(moreLbl: @Nls String?, pnl: (AtomicBooleanProperty) -> JComponent) {
    morePanelFactory = pnl
    moreLbl?.apply { moreLabel = this }
  }

}