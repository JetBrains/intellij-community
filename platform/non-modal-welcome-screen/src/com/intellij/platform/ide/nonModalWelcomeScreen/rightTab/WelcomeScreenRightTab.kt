package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.FeatureButtonModelWithBackend
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.WelcomeContent
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.KeymapModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.StartupSwitchModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.components.WelcomeScreenCustomButton
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.components.WelcomeScreenCustomListComboBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.modifier.onActivated
import org.jetbrains.jewel.foundation.modifier.trackComponentActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.ComboBoxColors
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import javax.swing.JComponent

@ApiStatus.Internal
class WelcomeScreenRightTab(
  val project: Project,
  val contentProvider: WelcomeRightTabContentProvider
) {
  val component: JComponent by lazy {
    JewelComposePanel {
      WelcomeScreen()
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  fun WelcomeScreen() {
    val dragAndDropTarget = remember {
      object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
          val files = FileCopyPasteUtil.getFiles(event.awtTransferable)
          return contentProvider.getFileDragAndDropHandler().openFiles(project, files)
        }
      }
    }
    val focusRequester = remember { FocusRequester() }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(color = panelBackgroundColor)
        .focusRequester(focusRequester)
        .trackComponentActivation(component)
        .onActivated { activated ->
          if (activated) {
            focusRequester.requestFocus(FocusDirection.Enter)
          }
        }
        .dragAndDropTarget(
          shouldStartDragAndDrop = { event ->
            event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
          },
          target = dragAndDropTarget
        )
    ) {
      Image(
        imageVector = backgroundImageVector,
        contentDescription = null,
        alignment = Alignment.TopCenter,
        // ImageVector has very sharp boundaries, apply the blur to soften them
        modifier = Modifier.fillMaxSize().blur(128.dp)
      )
      val scrollState = rememberScrollState()
      VerticallyScrollableContainer(
        scrollState = scrollState,
        modifier = Modifier.fillMaxSize(),
        style = JewelTheme.scrollbarStyle
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp).align(Alignment.Center),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(contentProvider.title.get(),
                   fontSize = 20.sp, lineHeight = 24.sp, color = fontColor)
              Spacer(modifier = Modifier.height(8.dp))
              Text(contentProvider.secondaryTitle.get(),
                   fontSize = 13.sp, lineHeight = 16.sp, color = secondaryFontColor)
            }
            Spacer(modifier = Modifier.height(32.dp))

            FeatureGrid(modifier = Modifier.wrapContentSize(Alignment.Center))

            val additionalComponents = contentProvider.getAdditionalComponents(project)
            if (additionalComponents.isNotEmpty()) {
              Spacer(modifier = Modifier.height(24.dp))
              Column(
                modifier = Modifier.wrapContentSize(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                for (row in additionalComponents) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                  ) {
                    for (component in row) {
                      when (component) {
                        is WelcomeContent.Text -> WelcomeScreenText(component)
                        is WelcomeContent.Link -> WelcomeScreenLink(component)
                      }
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(24.dp))

            WelcomeScreenRightTabBannerProvider.SingleBanner(project, modifier = Modifier.wrapContentSize(Alignment.Center))
        }
        var showOnStartup by remember { mutableStateOf(isRightTabEnabled) }
        Column(modifier = Modifier.align(Alignment.BottomCenter),
               horizontalAlignment = Alignment.CenterHorizontally) {
          SwitchPanel()

          if (contentProvider.isDisableOptionVisible) {
            CheckboxRow(text = NonModalWelcomeScreenBundle.message("welcome.screen.enabled.checkbox"),
                        textStyle = TextStyle(color = fontColor),
                        modifier = Modifier.padding(bottom = 12.dp),
                        checked = showOnStartup, onCheckedChange = {
              showOnStartup = it
              isRightTabEnabled = it
            })
          }
        }
      }
    }
  }

  @Composable
  fun FeatureGrid(modifier: Modifier = Modifier) {
    val coroutineScope = contentProvider.coroutineScope
    var backendFeatureIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(true) {
      coroutineScope.launch {
        backendFeatureIds = WelcomeScreenFeatureApi.getInstance().getAvailableFeatureIds().toSet()
      }
    }

    Column(modifier = modifier.wrapContentSize(Alignment.Center), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      // Show only available backend features (and all non-backend features)
      val featureModels = contentProvider.getFeatureButtonModels(project).filter {
        it !is FeatureButtonModelWithBackend || it.isAlwaysAvailable || it.featureKey in backendFeatureIds
      }

      for (row in featureModels.chunked(contentProvider.buttonsPerRow)) {
        Row(modifier = Modifier.wrapContentSize(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          for (model in row) {
            FeatureButton(model, coroutineScope)
          }
        }
      }
    }
  }

  @Composable
  private fun FeatureButton(model: WelcomeRightTabContentProvider.FeatureButtonModel, scope: CoroutineScope) {
    WelcomeScreenCustomButton(
      onClick = {
        model.onClick(project, scope)
      },
      style = CustomButtonStyle(),
      modifier = Modifier.size(128.dp, 96.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          key = model.icon,
          contentDescription = model.text,
          tint = model.tint,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          model.text,
          color = fontColor,
          fontSize = 13.sp,
          lineHeight = 16.sp,
          maxLines = 2,
          textAlign = TextAlign.Center
        )
      }
    }
  }

  @Composable
  fun SwitchPanel() {
    val additionalButtons = contentProvider.getAdditionalInfoButtonModels(project).map { ButtonInfoPanelModel(it) }
    val buttons = listOf(
      ComboBoxInfoPanelModel(themeIconKey,
                             "welcome.screen.right.tab.theme.switch.prefix",
                             ThemeModel()),
      ComboBoxInfoPanelModel(AllIconsKeys.General.Keyboard,
                             "welcome.screen.right.tab.keymap.switch.prefix",
                             KeymapModel()),
      ComboBoxInfoPanelModel(AllIconsKeys.General.Settings,
                             "welcome.screen.right.tab.startup.switch.prefix",
                             StartupSwitchModel()),
    ) + additionalButtons

    val buttonsPerRow = contentProvider.buttonsPerRow
    val coroutineScope = contentProvider.coroutineScope
    for (row in buttons.chunked(buttonsPerRow)) {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        for (model in row) {
          when (model) {
            is ComboBoxInfoPanelModel -> InfoPanelItem(model.iconKey, model.itemPrefix, model.model, project, getStatisticLogger(model))
            is ButtonInfoPanelModel -> InfoPanelButton(model.iconKey, model.itemPrefix, model.onClick, coroutineScope)
          }
        }
      }
    }
  }

  private fun getStatisticLogger(comboBoxInfoPanelModel: ComboBoxInfoPanelModel): ((String, Int) -> Unit)? {
    return when (comboBoxInfoPanelModel.model) {
      is ThemeModel -> { _, _ ->
        WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.THEME)
      }
      is KeymapModel -> { _, _ ->
        WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.KEYMAP)
      }
      is StartupSwitchModel -> { _, index ->
        WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.STARTUP)
        WelcomeScreenTabUsageCollector.logStartupOptionChanged(comboBoxInfoPanelModel.model.items[index])
      }
      else -> null
    }
  }


  sealed interface InfoPanelModel {
    val iconKey: IconKey
    val itemPrefix: String
  }

  private class ComboBoxInfoPanelModel(
    override val iconKey: IconKey,
    val itemPrefixKey: String,
    val model: WelcomeScreenRightTabComboBoxModel<out Any>,
  ) : InfoPanelModel {
    override val itemPrefix: String
      get() = NonModalWelcomeScreenBundle.message(itemPrefixKey)
  }

  private class ButtonInfoPanelModel(private val model: WelcomeRightTabContentProvider.InfoButtonModel) : InfoPanelModel {
    override val iconKey: IconKey
      get() = model.icon
    override val itemPrefix: String
      get() = model.text
    val onClick: (Project, CoroutineScope) -> Unit = model.onClick
  }

  @Composable
  internal fun InfoPanelItem(
    iconKey: IconKey,
    itemPrefix: String,
    model: WelcomeScreenRightTabComboBoxModel<out Any>,
    project: Project,
    afterOnSelectedItemChanged: ((newSelection: String, index: Int) -> Unit)? = null
  ) {
    Box {
      WelcomeScreenCustomListComboBox(
        iconKey = iconKey,
        itemPrefix = itemPrefix,
        items = model.itemNames(),
        style = CustomComboBoxStyle(),
        maxPopupHeight = 300.dp,
        minPopupWidth = 200.dp,
        externalUpdateListener = model.externalUpdateListener(project),
        initialSelectedIndex = model.currentItemIndex(),
        onSelectedItemChange = { index, newSelection ->
          model.setByIndex(index, newSelection)
          afterOnSelectedItemChanged?.invoke(newSelection, index)
        },
      ) { item, isSelected, isActive ->
        SimpleListItem(
          text = item,
          isSelected = isSelected,
          isActive = isActive,
          iconContentDescription = item,
        )
      }
    }
  }

  @Composable
  private fun InfoPanelButton(
    icon: IconKey,
    text: String,
    onClick: (Project, CoroutineScope) -> Unit,
    coroutineScope: CoroutineScope,
  ) {
    WelcomeScreenCustomButton(
      onClick = {
        onClick(project, coroutineScope)
      },
      style = InfoPanelButtonStyle(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.focusable(false).focusProperties { canFocus = false }) {
        Box(contentAlignment = Alignment.Center) {
          Icon(key = icon, contentDescription = text)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = fontColor, maxLines = 1, style = JewelTheme.defaultTextStyle, overflow = TextOverflow.Ellipsis)
      }
    }
  }

  @Composable
  fun InfoPanelButtonStyle(): ButtonStyle {
    val defaultButtonStyle = JewelTheme.defaultButtonStyle
    return ButtonStyle(
      colors = with(defaultButtonStyle.colors) {
        ButtonColors(
          background = SolidColor(Color.Transparent),
          backgroundDisabled = backgroundDisabled,
          backgroundFocused = SolidColor(Color.Transparent),
          backgroundPressed = featureButtonBackgroundPressedColor,
          backgroundHovered = featureButtonBackgroundHoveredColor,
          content = content,
          contentDisabled = contentDisabled,
          contentFocused = contentFocused,
          contentPressed = contentPressed,
          contentHovered = contentHovered,
          border = border,
          borderDisabled = borderDisabled,
          borderFocused = borderFocused,
          borderPressed = border,
          borderHovered = border,
        )
      },
      metrics = defaultButtonStyle.metrics,
      focusOutlineAlignment = defaultButtonStyle.focusOutlineAlignment
    )
  }

  @Composable
  fun CustomComboBoxStyle(): ComboBoxStyle = ComboBoxStyle(
    colors = with(JewelTheme.comboBoxStyle.colors) {
      ComboBoxColors(
        background = featureComboBoxBackgroundColor,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = featureComboBoxBackgroundFocusedColor,
        backgroundPressed = featureComboBoxBackgroundPressedColor,
        backgroundHovered = featureComboBoxBackgroundHoveredColor,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        border = border,
        borderDisabled = borderDisabled,
        borderFocused = borderFocused,
        borderPressed = border,
        borderHovered = border,
        nonEditableBackground = nonEditableBackground,
      )
    },
    metrics = JewelTheme.comboBoxStyle.metrics,
    icons = JewelTheme.comboBoxStyle.icons,
  )

  @get:Composable
  private val featureComboBoxBackgroundColor: Color
    get() = Color.Transparent

  @get:Composable
  private val featureComboBoxBackgroundFocusedColor: Color
    get() = Color.Transparent

  @get:Composable
  private val featureComboBoxBackgroundPressedColor: Color
    get() = when (JewelTheme.isDark) {
      true -> Color.White.copy(alpha = 0.18f)
      false -> Color.Black.copy(alpha = 0.12f)
    }

  @get:Composable
  private val featureComboBoxBackgroundHoveredColor: Color
    get() = when (JewelTheme.isDark) {
      true -> Color.White.copy(alpha = 0.12f)
      false -> Color.Black.copy(alpha = 0.08f)
    }

  @Composable
  fun CustomButtonStyle(): ButtonStyle {
    val defaultButtonStyle = JewelTheme.defaultButtonStyle

    // Copy default colors, modify the background properties for transparency, and remove the border
    val customButtonColors = with(defaultButtonStyle.colors) {
      ButtonColors(
        background = featureButtonBackgroundColor,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = featureButtonBackgroundPressedColor,
        backgroundPressed = featureButtonBackgroundPressedColor,
        backgroundHovered = featureButtonBackgroundHoveredColor,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        border = emptyList<Color>().createVerticalBrush(),
        borderDisabled = borderDisabled,
        borderFocused = borderFocused,
        borderPressed = borderPressed,
        borderHovered = borderHovered
      )
    }

    val customMetrics = with(defaultButtonStyle.metrics) {
      ButtonMetrics(
        cornerSize = CornerSize(8.dp),
        minSize = minSize,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        borderWidth = 0.dp,
        focusOutlineExpand = 0.dp,
      )
    }

    return ButtonStyle(
      colors = customButtonColors,
      metrics = customMetrics,
      focusOutlineAlignment = defaultButtonStyle.focusOutlineAlignment
    )
  }

  @Composable
  private fun WelcomeScreenText(model: WelcomeContent.Text) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = model.text,
        color = model.tint.takeIf { it != Color.Unspecified } ?: fontColor,
        fontSize = 13.sp,
        lineHeight = 16.sp,
      )
      model.icon?.let { icon ->
        Icon(
          key = icon,
          contentDescription = null,
        )
      }
    }
  }

  @Composable
  private fun WelcomeScreenLink(model: WelcomeContent.Link) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val restingColor = model.tint.takeIf { it != Color.Unspecified } ?: fontColor
    val textColor = if (isHovered) {
      model.tintHovered.takeIf { it != Color.Unspecified } ?: restingColor
    } else {
      restingColor
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier
        .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
        .hoverable(interactionSource)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = { model.onClick(project) },
        ),
    ) {
      Text(
        text = model.text,
        color = textColor,
        fontSize = 13.sp,
        lineHeight = 16.sp,
      )
      Icon(
        key = AllIconsKeys.Ide.External_link_arrow,
        contentDescription = null,
        tint = restingColor,
        modifier = Modifier.size(14.dp),
      )
    }
  }


  private val fontColor
    get() = retrieveColorOrUnspecified("*.foreground")

  @get:Composable
  private val secondaryFontColor
    get() = color(dark = JewelTheme.colorPalette.grayOrNull(10),
                  light = JewelTheme.colorPalette.grayOrNull(1),
                  fallback = Color(0xFFB4B8BF))

  private val panelBackgroundColor
    get() = retrieveColorOrUnspecified("EditorTabs.background")

  @get:Composable
  private val featureButtonBackgroundColor: SolidColor
    get() = when (JewelTheme.isDark) {
      true -> SolidColor(Color.White.copy(alpha = 0.07f))
      false -> SolidColor(Color.Black.copy(alpha = 0.05f))
    }

  @get:Composable
  private val featureButtonBackgroundHoveredColor: SolidColor
    get() = when (JewelTheme.isDark) {
      true -> SolidColor(Color.White.copy(alpha = 0.12f))
      false -> SolidColor(Color.Black.copy(alpha = 0.08f))
    }

  @get:Composable
  private val featureButtonBackgroundPressedColor: SolidColor
    get() = when (JewelTheme.isDark) {
      true -> SolidColor(Color.White.copy(alpha = 0.18f))
      false -> SolidColor(Color.Black.copy(alpha = 0.12f))
    }

  @get:Composable
  private val backgroundImageVector: ImageVector
    get() = when (JewelTheme.isDark) {
      true -> contentProvider.backgroundImageVectorDark
      false -> contentProvider.backgroundImageVectorLight
    }

  @get:Composable
  private val themeIconKey: IconKey
    get() = when (JewelTheme.isDark) {
      true -> AllIconsKeys.MeetNewUi.DarkTheme
      false -> AllIconsKeys.MeetNewUi.LightTheme
    }

  companion object {
    @ApiStatus.Internal
    suspend fun show(project: Project) {
      if (!isRightTabEnabled) return
      val contentProvider = WelcomeRightTabContentProvider.getSingleExtension() ?: return
      withContext(Dispatchers.EDT) {
        val settingsFile = WelcomeScreenRightTabVirtualFile(WelcomeScreenRightTab(project, contentProvider), project)
        val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
        val options = FileEditorOpenOptions(reuseOpen = true, isSingletonEditorInWindow = true,
                                            selectAsCurrent = contentProvider.shouldBeFocused(project))
        fileEditorManager.openFile(settingsFile, options)
        WelcomeScreenTabUsageCollector.logWelcomeScreenTabOpened()
      }
    }

    private var isRightTabEnabled: Boolean
      get() = AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID)
      set(value) = AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, value)

    @Composable
    fun color(dark: Color?, light: Color?, fallback: Color): Color {
      val themeColor = if (JewelTheme.isDark) dark else light
      return themeColor ?: fallback
    }
  }
}