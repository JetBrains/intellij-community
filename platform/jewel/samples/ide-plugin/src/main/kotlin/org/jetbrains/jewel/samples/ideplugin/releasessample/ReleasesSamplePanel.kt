package org.jetbrains.jewel.samples.ideplugin.releasessample

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ReleasesSamplePanel(scope: CoroutineScope) : BorderLayoutPanel() {
    private val sidePanel = DetailsPanel(scope)

    private var currentContentSource: ContentSource<*> = AndroidStudioReleases

    private val filterTextField =
        SearchTextField(false).apply {
            addDocumentListener(
                object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent) {
                        filterContent(text)
                    }

                    override fun removeUpdate(e: DocumentEvent) {
                        filterContent(text)
                    }

                    override fun changedUpdate(e: DocumentEvent) {
                        filterContent(text)
                    }
                },
            )
        }

    private val actions: List<AnAction> =
        listOf(
            object : CheckboxAction(AndroidStudioReleases.displayName), DumbAware {
                override fun isSelected(e: AnActionEvent): Boolean = currentContentSource == AndroidStudioReleases

                override fun setSelected(
                    e: AnActionEvent,
                    state: Boolean,
                ) {
                    setContentSource(AndroidStudioReleases)
                }

                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            },
            object : CheckboxAction(AndroidReleases.displayName), DumbAware {
                override fun isSelected(e: AnActionEvent): Boolean = currentContentSource == AndroidReleases

                override fun setSelected(
                    e: AnActionEvent,
                    state: Boolean,
                ) {
                    setContentSource(AndroidReleases)
                }

                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            },
        )

    private val overflowAction =
        MoreActionGroup()
            .apply { addAll(actions) }

    private val overflowActionButton: ActionButton =
        ActionButton(
            overflowAction,
            overflowAction.templatePresentation.clone(),
            "JewelSwingDemoTopBar",
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE,
        )

    private val topBar =
        BorderLayoutPanel().apply {
            addToLeft(JBLabel("Filter elements: "))
            addToCenter(filterTextField)
            addToRight(overflowActionButton)
            border = JBUI.Borders.empty(4)
        }

    private var lastSelected: ContentItem? = null
    private val contentList =
        JBList<ContentItem>().apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION

            addListSelectionListener {
                if (selectedValue != lastSelected) {
                    lastSelected = selectedValue
                    onListSelectionChanged()
                }
            }
        }

    private val mainPanel =
        BorderLayoutPanel().apply {
            addToTop(topBar)

            val scrollPane =
                JBScrollPane(contentList).apply {
                    setBorder(JBUI.Borders.empty())
                    setViewportBorder(JBUI.Borders.empty())
                    horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                }

            addToCenter(scrollPane)
        }

    init {
        val splitter = OnePixelSplitter(false, .7f, .25f, .75f)
        splitter.firstComponent = mainPanel
        splitter.secondComponent = sidePanel
        splitter.foreground
        addToCenter(splitter)

        contentList.installCellRenderer {
            BorderLayoutPanel(JBUIScale.scale(4), 0).apply {
                border = JBUI.Borders.empty(0, 4)

                addToCenter(JBLabel(it.displayText))

                if (it is ContentItem.AndroidStudio) {
                    addToRight(
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.LINE_AXIS)
                            isOpaque = false
                            add(ChannelIndication(it.channel))
                        },
                    )
                } else if (it is ContentItem.AndroidRelease) {
                    addToRight(
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.LINE_AXIS)
                            isOpaque = false
                            add(ApiLevelIndication(it.apiLevel))
                        },
                    )
                }
            }
        }

        setContentSource(AndroidStudioReleases)
    }

    private fun setContentSource(contentSource: ContentSource<*>) {
        currentContentSource = contentSource

        contentList.model = JBList.createDefaultListModel(contentSource.items)
    }

    private fun filterContent(text: String) {
        val model = contentList.model as DefaultListModel<ContentItem>

        val normalizedFilter = text.trim()

        model.clear()
        model.addAll(currentContentSource.items.filter { it.matches(normalizedFilter) })
    }

    private fun onListSelectionChanged() {
        val selection = contentList.selectedValue
        sidePanel.display(selection)

        revalidate()
        repaint()
    }
}
