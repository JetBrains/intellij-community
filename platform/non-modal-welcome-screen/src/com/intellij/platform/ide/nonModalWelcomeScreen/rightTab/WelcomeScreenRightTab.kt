package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import com.intellij.ide.dnd.FileCopyPasteUtil
import java.awt.datatransfer.DataFlavor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.GoFileDragAndDropHandler
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.KeymapModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.components.WelcomeScreenCustomButton
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.components.WelcomeScreenCustomListComboBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.*
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
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
          return GoFileDragAndDropHandler.openFiles(project, files)
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(color = panelBackgroundColor)
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
            Spacer(modifier = Modifier.height(24.dp))

            WelcomeScreenRightTabBannerProvider.SingleBanner(project, modifier = Modifier.wrapContentSize(Alignment.Center))
        }
        var showOnStartup by remember { mutableStateOf(isRightTabEnabled) }
        Column(modifier = Modifier.align(Alignment.BottomCenter),
               horizontalAlignment = Alignment.CenterHorizontally) {
          SwitchPanel()

          CheckboxRow(text = NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.always.show.on.startup"),
                      textStyle = TextStyle(color = fontColor),
                      modifier = Modifier.padding(vertical = 12.dp),
                      checked = showOnStartup, onCheckedChange = {
            showOnStartup = it
            isRightTabEnabled = it
          })
        }
      }
    }
  }

  @Composable
  fun FeatureGrid(modifier: Modifier = Modifier) {
    Column(modifier = modifier.wrapContentSize(Alignment.Center), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      val featureModels = contentProvider.getFeatureButtonModels(project)
      for (row in featureModels.chunked(3)) {
        Row(modifier = Modifier.wrapContentSize(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          for (model in row) {
            FeatureButton(model)
          }
        }
      }
    }
  }

  @Composable
  private fun FeatureButton(model: WelcomeRightTabContentProvider.FeatureButtonModel) {
    WelcomeScreenCustomButton(
      onClick = {
        model.onClick()
      },
      style = CustomButtonStyle(),
      modifier = Modifier.size(112.dp, 87.dp),
    ) {
      Column {
        Icon(key = model.icon, contentDescription = model.text, tint = model.tint,
             modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(8.dp))
        Text(model.text, color = fontColor,
             fontSize = 13.sp, lineHeight = 16.sp,
             modifier = Modifier.align(Alignment.CenterHorizontally))
      }
    }
  }

  @Composable
  fun SwitchPanel() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      InfoPanelItem(themeIconKey,
                    NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.switch.prefix"),
                    ThemeModel())
      InfoPanelItem(AllIconsKeys.General.Keyboard,
                    NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.keymap.switch.prefix"),
                    KeymapModel())
    }
  }

  @Composable
  internal fun InfoPanelItem(iconKey: IconKey, itemPrefix: String, model: WelcomeScreenRightTabComboBoxModel<out Any>) {
    Box {
      WelcomeScreenCustomListComboBox(
        iconKey = iconKey,
        itemPrefix = itemPrefix,
        items = model.itemNames(),
        style = CustomComboBoxStyle(),
        maxPopupHeight = 300.dp,
        minPopupWidth = 200.dp,
        initialSelectedIndex = model.currentItemIndex(),
        onSelectedItemChange = { index, newSelection ->
          model.setByIndex(index, newSelection)
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
        padding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
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