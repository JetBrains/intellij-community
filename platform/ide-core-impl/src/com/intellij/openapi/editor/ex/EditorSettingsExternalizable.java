// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.ui.UINumericRange;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.actions.CaretStopOptions;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;
import java.util.stream.Collectors;

@State(name = "EditorSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE, perClient = true)
public class EditorSettingsExternalizable implements PersistentStateComponent<EditorSettingsExternalizable.OptionSet> {
  /**
   * @deprecated Use {@link PropNames#PROP_IS_VIRTUAL_SPACE} instead
   */
  @Deprecated(forRemoval = true)
  public static final @NonNls String PROP_VIRTUAL_SPACE = PropNames.PROP_IS_VIRTUAL_SPACE;
  /**
   * @deprecated Use {@link PropNames#PROP_BREADCRUMBS_PER_LANGUAGE} instead
   */
  @Deprecated(forRemoval = true)
  public static final @NonNls String PROP_BREADCRUMBS_PER_LANGUAGE = PropNames.PROP_BREADCRUMBS_PER_LANGUAGE;

  /**
   * @deprecated Use {@link PropNames#PROP_ENABLE_RENDERED_DOC} instead
   */
  @Deprecated(forRemoval = true)
  public static final @NonNls String PROP_DOC_COMMENT_RENDERING = PropNames.PROP_ENABLE_RENDERED_DOC;

  public static final UINumericRange BLINKING_RANGE = new UINumericRange(500, 10, 1500);
  public static final UINumericRange TOOLTIPS_DELAY_RANGE = new UINumericRange(500, 1, 5000);

  private static final String SOFT_WRAP_FILE_MASKS_ENABLED_DEFAULT = "*";
  private static final @NonNls String SOFT_WRAP_FILE_MASKS_DISABLED_DEFAULT = "*.md; *.txt; *.rst; *.adoc";

  //Q: make it interface?
  public static final class OptionSet {
    // todo: unused? schedule for removal?
    public String LINE_SEPARATOR;
    public String USE_SOFT_WRAPS;
    public String SOFT_WRAP_FILE_MASKS;
    public boolean USE_CUSTOM_SOFT_WRAP_INDENT = true;
    public int CUSTOM_SOFT_WRAP_INDENT = 0;
    public boolean IS_VIRTUAL_SPACE = false;
    public int VERTICAL_SCROLL_OFFSET = 1;
    public int VERTICAL_SCROLL_JUMP = 0;
    public int HORIZONTAL_SCROLL_OFFSET = 3;
    public int HORIZONTAL_SCROLL_JUMP = 0;
    public boolean IS_CARET_INSIDE_TABS;
    public @NonNls String STRIP_TRAILING_SPACES = STRIP_TRAILING_SPACES_CHANGED;
    public boolean IS_ENSURE_NEWLINE_AT_EOF = false;
    public boolean REMOVE_TRAILING_BLANK_LINES = false;
    public boolean SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT = true;
    public boolean SHOW_INSPECTION_WIDGET = true;
    public int TOOLTIPS_DELAY_MS = TOOLTIPS_DELAY_RANGE.initial;
    public boolean SHOW_INTENTION_BULB = true;
    public boolean IS_CARET_BLINKING = true;
    public int CARET_BLINKING_PERIOD = BLINKING_RANGE.initial;
    public boolean IS_RIGHT_MARGIN_SHOWN = true;
    public boolean ARE_LINE_NUMBERS_SHOWN = true;
    public boolean ARE_LINE_NUMBERS_AFTER_ICONS = false;
    public @NotNull EditorSettings.LineNumerationType LINE_NUMERATION = EditorSettings.LineNumerationType.ABSOLUTE;
    public boolean ARE_GUTTER_ICONS_SHOWN = true;
    public boolean IS_FOLDING_OUTLINE_SHOWN = true;
    public boolean IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER = true;
    public boolean IS_FOLDING_ENDINGS_SHOWN = false; //is not used in old UI
    public boolean SHOW_BREADCRUMBS_ABOVE = false;
    public boolean SHOW_BREADCRUMBS = true;
    public boolean SHOW_STICKY_LINES = true;
    public int STICKY_LINES_LIMIT = 5;
    public boolean ENABLE_RENDERED_DOC = false;
    public boolean SHOW_INTENTION_PREVIEW = true;
    public boolean USE_EDITOR_FONT_IN_INLAYS = false;

    public boolean SMART_HOME = true;

    public boolean IS_BLOCK_CURSOR = false;
    public boolean IS_FULL_LINE_HEIGHT_CURSOR = false;
    public boolean IS_HIGHLIGHT_SELECTION_OCCURRENCES = false;
    public boolean IS_WHITESPACES_SHOWN = false;
    public boolean IS_LEADING_WHITESPACES_SHOWN = true;
    public boolean IS_INNER_WHITESPACES_SHOWN = true;
    public boolean IS_TRAILING_WHITESPACES_SHOWN = true;
    public boolean IS_SELECTION_WHITESPACES_SHOWN = true;
    @SuppressWarnings("SpellCheckingInspection")
    public boolean IS_ALL_SOFTWRAPS_SHOWN = false;
    public boolean IS_INDENT_GUIDES_SHOWN = true;
    public boolean IS_FOCUS_MODE = false;
    public boolean IS_ANIMATED_SCROLLING = true;
    public boolean IS_CAMEL_WORDS = false;
    public boolean ADDITIONAL_PAGE_AT_BOTTOM = false;

    public boolean IS_DND_ENABLED = true;
    @SuppressWarnings("SpellCheckingInspection")
    public boolean IS_WHEEL_FONTCHANGE_ENABLED = false;
    public boolean IS_WHEEL_FONTCHANGE_PERSISTENT = false;
    public boolean IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS = true;

    public boolean RENAME_VARIABLES_INPLACE = true;
    public boolean PRESELECT_RENAME = true;
    public boolean SHOW_INLINE_DIALOG = true;

    public boolean REFRAIN_FROM_SCROLLING = false;

    public boolean ADD_CARETS_ON_DOUBLE_CTRL = true;

    public BidiTextDirection BIDI_TEXT_DIRECTION = BidiTextDirection.CONTENT_BASED;

    // todo: useful? schedule for removal?
    public boolean SHOW_PARAMETER_NAME_HINTS = true;

    public boolean KEEP_TRAILING_SPACE_ON_CARET_LINE = true;

    public boolean INSERT_PARENTHESES_AUTOMATICALLY = true;

    private final Map<String, Boolean> mapLanguageBreadcrumbs = new HashMap<>();

    private final Map<String, Boolean> mapLanguageStickyLines = new HashMap<>();

    public Map<String, Boolean> getLanguageBreadcrumbsMap() {
      return mapLanguageBreadcrumbs;
    }

    public Map<String, Boolean> getLanguageStickyLines() {
      return mapLanguageStickyLines;
    }

    public OptionSet() {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        PropertiesComponent properties = PropertiesComponent.getInstance();
        if (properties != null) {
          INSERT_PARENTHESES_AUTOMATICALLY = properties.getBoolean("js.insert.parentheses.on.completion", true);
        }
      }
    }

    @SuppressWarnings("unused")
    public void setLanguageBreadcrumbsMap(Map<String, Boolean> map) {
      if (this.mapLanguageBreadcrumbs != map) {
        this.mapLanguageBreadcrumbs.clear();
        this.mapLanguageBreadcrumbs.putAll(map);
      }
    }

    @SuppressWarnings("unused")
    public void setLanguageStickyLines(Map<String, Boolean> map) {
      if (this.mapLanguageStickyLines != map) {
        this.mapLanguageStickyLines.clear();
        this.mapLanguageStickyLines.putAll(map);
      }
    }
  }

  @State(
    name = "OsSpecificEditorSettings",
    storages = @Storage(value = "editor.os-specific.xml", roamingType = RoamingType.PER_OS),
    category = SettingsCategory.CODE
  )
  public static final class OsSpecificState implements PersistentStateComponent<OsSpecificState> {
    public CaretStopOptions CARET_STOP_OPTIONS = new CaretStopOptions();

    @Override
    public OsSpecificState getState() {
      return this;
    }

    @Override
    public void loadState(@NotNull OsSpecificState state) {
      CARET_STOP_OPTIONS = state.CARET_STOP_OPTIONS;
    }
  }

  private static final String COMPOSITE_PROPERTY_SEPARATOR = ":";

  private final @NotNull OsSpecificState myOsSpecificState;

  private final Set<SoftWrapAppliancePlaces> myPlacesToUseSoftWraps = EnumSet.noneOf(SoftWrapAppliancePlaces.class);
  private OptionSet myOptions = new OptionSet();
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final Map<String, Boolean> myDefaultBreadcrumbVisibility = new HashMap<>();

  private int myBlockIndent;

  public static final @NonNls String STRIP_TRAILING_SPACES_NONE = "None";
  public static final @NonNls String STRIP_TRAILING_SPACES_CHANGED = "Changed";
  public static final @NonNls String STRIP_TRAILING_SPACES_WHOLE = "Whole";

  @MagicConstant(stringValues = {STRIP_TRAILING_SPACES_NONE, STRIP_TRAILING_SPACES_CHANGED, STRIP_TRAILING_SPACES_WHOLE})
  public @interface StripTrailingSpaces {}

  public EditorSettingsExternalizable() {
    this(ApplicationManager.getApplication().getService(OsSpecificState.class));
  }

  @NonInjectable
  public EditorSettingsExternalizable(@NotNull OsSpecificState state) {
    myOsSpecificState = state;
  }

  @RequiresBlockingContext
  public static EditorSettingsExternalizable getInstance() {
    return ApplicationManager.getApplication().getService(EditorSettingsExternalizable.class);
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable disposable) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
    Disposer.register(disposable, () -> myPropertyChangeSupport.removePropertyChangeListener(listener));
  }

  protected void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
    myPropertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  @Override
  public @NotNull OptionSet getState() {
    return myOptions;
  }

  @Override
  public void loadState(@NotNull OptionSet state) {
    myOptions = state;
    parseRawSoftWraps();
  }

  @Override
  public void noStateLoaded() {
    loadState(new OptionSet());
  }

  private void parseRawSoftWraps() {
    myPlacesToUseSoftWraps.clear();

    if (StringUtil.isEmpty(myOptions.USE_SOFT_WRAPS)) {
      return;
    }

    String[] placeNames = myOptions.USE_SOFT_WRAPS.split(COMPOSITE_PROPERTY_SEPARATOR);
    for (String placeName : placeNames) {
      try {
        SoftWrapAppliancePlaces place = SoftWrapAppliancePlaces.valueOf(placeName);
        myPlacesToUseSoftWraps.add(place);
      }
      catch (IllegalArgumentException e) {
        // Ignore bad value
      }
    }

    // There is a possible case that there were invalid/old format values. We want to replace them by up-to-date data.
    storeRawSoftWraps();
  }

  private void storeRawSoftWraps() {
    StringBuilder buffer = new StringBuilder();
    for (SoftWrapAppliancePlaces placeToStore : myPlacesToUseSoftWraps) {
      buffer.append(placeToStore).append(COMPOSITE_PROPERTY_SEPARATOR);
    }
    if (!buffer.isEmpty()) {
      buffer.setLength(buffer.length() - 1);
    }
    String newValue = buffer.toString();

    String old = myOptions.USE_SOFT_WRAPS;
    if (newValue.equals(old)) return;  // `newValue` is not null
    myOptions.USE_SOFT_WRAPS = newValue;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_USE_SOFT_WRAPS, old, newValue);
  }

  public OptionSet getOptions() {
    return myOptions;
  }

  public boolean isRightMarginShown() {
    return myOptions.IS_RIGHT_MARGIN_SHOWN;
  }

  public void setRightMarginShown(boolean val) {
    boolean old = myOptions.IS_RIGHT_MARGIN_SHOWN;
    if (old == val) return;
    myOptions.IS_RIGHT_MARGIN_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_RIGHT_MARGIN_SHOWN, old, val);
  }

  public boolean isLineNumbersShown() {
    return myOptions.ARE_LINE_NUMBERS_SHOWN;
  }

  public void setLineNumbersShown(boolean val) {
    boolean old = myOptions.ARE_LINE_NUMBERS_SHOWN;
    if (old == val) return;
    myOptions.ARE_LINE_NUMBERS_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_ARE_LINE_NUMBERS_SHOWN, old, val);
  }

  public boolean isLineNumbersAfterIcons() {
    return myOptions.ARE_LINE_NUMBERS_AFTER_ICONS;
  }

  public EditorSettings.LineNumerationType getLineNumeration() {
    return myOptions.LINE_NUMERATION;
  }

  public void setLineNumeration(EditorSettings.LineNumerationType val) {
    EditorSettings.LineNumerationType old = myOptions.LINE_NUMERATION;
    if (old == val) return;
    myOptions.LINE_NUMERATION = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_LINE_NUMERATION, old, val);

  }

  public boolean areGutterIconsShown() {
    return myOptions.ARE_GUTTER_ICONS_SHOWN;
  }

  public void setGutterIconsShown(boolean val) {
    boolean old = myOptions.ARE_GUTTER_ICONS_SHOWN;
    if (old == val) return;
    myOptions.ARE_GUTTER_ICONS_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_ARE_GUTTER_ICONS_SHOWN, old, val);
  }

  public boolean isFoldingOutlineShown() {
    return myOptions.IS_FOLDING_OUTLINE_SHOWN;
  }

  public void setFoldingOutlineShown(boolean val) {
    boolean old = myOptions.IS_FOLDING_OUTLINE_SHOWN;
    if (old == val) return;
    myOptions.IS_FOLDING_OUTLINE_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_FOLDING_OUTLINE_SHOWN, old, val);
  }

  public boolean isFoldingOutlineShownOnlyOnHover() {
    return myOptions.IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER;
  }

  public void setFoldingOutlineShownOnlyOnHover(boolean val) {
    boolean old = myOptions.IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER;
    if (old == val) return;
    myOptions.IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER, old, val);
  }

  public boolean isFoldingEndingsShown() {
    return myOptions.IS_FOLDING_ENDINGS_SHOWN;
  }

  public void setFoldingEndingsShown(boolean val) {
    boolean old = myOptions.IS_FOLDING_ENDINGS_SHOWN;
    if (old == val) return;
    myOptions.IS_FOLDING_ENDINGS_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_FOLDING_ENDINGS_SHOWN, old, val);
  }

  /**
   * @return {@code true} if breadcrumbs should be shown above the editor, {@code false} otherwise
   */
  public boolean isBreadcrumbsAbove() {
    return myOptions.SHOW_BREADCRUMBS_ABOVE;
  }

  /**
   * @param value {@code true} if breadcrumbs should be shown above the editor, {@code false} otherwise
   * @return {@code true} if an option was modified, {@code false} otherwise
   */
  public boolean setBreadcrumbsAbove(boolean value) {
    boolean old = myOptions.SHOW_BREADCRUMBS_ABOVE;
    if (old == value) return false;
    myOptions.SHOW_BREADCRUMBS_ABOVE = value;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_BREADCRUMBS_ABOVE, old, value);
    return true;
  }

  /**
   * @return {@code true} if breadcrumbs should be shown, {@code false} otherwise
   */
  public boolean isBreadcrumbsShown() {
    return myOptions.SHOW_BREADCRUMBS;
  }

  /**
   * @param value {@code true} if breadcrumbs should be shown, {@code false} otherwise
   * @return {@code true} if an option was modified, {@code false} otherwise
   */
  public boolean setBreadcrumbsShown(boolean value) {
    boolean old = myOptions.SHOW_BREADCRUMBS;
    if (old == value) return false;
    myOptions.SHOW_BREADCRUMBS = value;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_BREADCRUMBS, old, value);
    return true;
  }

  /**
   * @param languageID the language identifier to configure
   * @return {@code true} if breadcrumbs should be shown for the specified language, {@code false} otherwise
   */
  public boolean isBreadcrumbsShownFor(String languageID) {
    Boolean visible = myOptions.mapLanguageBreadcrumbs.get(languageID);
    if (visible == null) {
      Boolean defaultVisible = getDefaultBreadcrumbVisibility(languageID);
      return defaultVisible == null || defaultVisible;
    }
    return visible;
  }

  private @Nullable Boolean getDefaultBreadcrumbVisibility(@NotNull String languageID) {
    Boolean defaultVisible = myDefaultBreadcrumbVisibility.get(languageID);
    if (defaultVisible == null) {
      for (BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensionList()) {
        for (Language language : provider.getLanguages()) {
          myDefaultBreadcrumbVisibility.put(language.getID(), provider.isShownByDefault());
        }
      }
      defaultVisible = myDefaultBreadcrumbVisibility.get(languageID);
    }
    return defaultVisible;
  }

  public void resetDefaultBreadcrumbVisibility() {
    myDefaultBreadcrumbVisibility.clear();
  }

  public boolean hasBreadcrumbSettings(String languageID) {
    return myOptions.mapLanguageBreadcrumbs.containsKey(languageID);
  }

  @ApiStatus.Internal
  public boolean hasDefaultBreadcrumbSettings(String languageID) {
    return getDefaultBreadcrumbVisibility(languageID) != null;
  }

  /**
   * @param languageID the language identifier to configure
   * @param value      {@code true} if breadcrumbs should be shown for the specified language, {@code false} otherwise
   * @return {@code true} if an option was modified, {@code false} otherwise
   */
  public boolean setBreadcrumbsShownFor(String languageID, boolean value) {
    Boolean visible = myOptions.mapLanguageBreadcrumbs.put(languageID, value);
    boolean newValue = (visible == null || visible) != value;
    if (newValue) {
      myPropertyChangeSupport.firePropertyChange(PROP_BREADCRUMBS_PER_LANGUAGE, visible, (Boolean)value);
    }
    return newValue;
  }

  public boolean areStickyLinesShown() {
    return myOptions.SHOW_STICKY_LINES;
  }

  public boolean setStickyLinesShown(boolean value) {
    boolean old = myOptions.SHOW_STICKY_LINES;
    if (old == value) return false;
    myOptions.SHOW_STICKY_LINES = value;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_STICKY_LINES, old, value);
    return true;
  }

  public boolean areStickyLinesShownFor(String languageID) {
    Boolean visible = myOptions.mapLanguageStickyLines.get(languageID);
    if (visible == null) {
      // enabled for all languages by default
      return true;
    }
    return visible;
  }

  @ApiStatus.Internal
  public List<String> getDisabledStickyLines() {
    return myOptions.mapLanguageStickyLines.entrySet().stream()
      .filter(entry -> !entry.getValue())
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());
  }

  public boolean setStickyLinesShownFor(String languageID, boolean value) {
    Boolean visible = myOptions.mapLanguageStickyLines.put(languageID, value);
    boolean newValue = (visible == null || visible) != value;
    if (newValue) {
      myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_STICKY_LINES_PER_LANGUAGE, visible, (Boolean)value);
    }
    return newValue;
  }

  public int getStickyLineLimit() {
    return myOptions.STICKY_LINES_LIMIT;
  }

  public void setStickyLineLimit(int limit) {
    int old = myOptions.STICKY_LINES_LIMIT;
    if (old == limit) return;
    myOptions.STICKY_LINES_LIMIT = limit;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_STICKY_LINES_LIMIT, old, limit);
  }

  public boolean isDocCommentRenderingEnabled() {
    return myOptions.ENABLE_RENDERED_DOC;
  }

  public void setDocCommentRenderingEnabled(boolean value) {
    boolean oldValue = myOptions.ENABLE_RENDERED_DOC;
    myOptions.ENABLE_RENDERED_DOC = value;
    if (oldValue != value) {
      myPropertyChangeSupport.firePropertyChange(PROP_DOC_COMMENT_RENDERING, oldValue, value);
    }
  }

  public boolean isBlockCursor() {
    return myOptions.IS_BLOCK_CURSOR;
  }

  public void setBlockCursor(boolean val) {
    boolean old = myOptions.IS_BLOCK_CURSOR;
    if (old == val) return;
    myOptions.IS_BLOCK_CURSOR = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_BLOCK_CURSOR, old, val);
  }

  public boolean isFullLineHeightCursor() {
    return myOptions.IS_FULL_LINE_HEIGHT_CURSOR;
  }

  public void setFullLineHeightCursor(boolean val) {
    boolean old = myOptions.IS_FULL_LINE_HEIGHT_CURSOR;
    if (old == val) return;
    myOptions.IS_FULL_LINE_HEIGHT_CURSOR = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_FULL_LINE_HEIGHT_CURSOR, old, val);
  }
  
  public boolean isHighlightSelectionOccurrences() {
    return myOptions.IS_HIGHLIGHT_SELECTION_OCCURRENCES;
  }
  
  public void setHighlightSelectionOccurrences(boolean val) {
    boolean old = myOptions.IS_HIGHLIGHT_SELECTION_OCCURRENCES;
    if (old == val) return;
    myOptions.IS_HIGHLIGHT_SELECTION_OCCURRENCES = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_HIGHLIGHT_SELECTION_OCCURRENCES, old, val);
  }

  public boolean isCaretRowShown() {
    return true;
  }

  public int getBlockIndent() {
    return myBlockIndent;
  }

  public void setBlockIndent(int blockIndent) {
    myBlockIndent = blockIndent;
  }

  public boolean isSmartHome() {
    return myOptions.SMART_HOME;
  }

  public void setSmartHome(boolean val) {
    boolean old = myOptions.SMART_HOME;
    if (old == val) return;
    myOptions.SMART_HOME = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SMART_HOME, old, val);
  }

  public boolean isUseSoftWraps() {
    return isUseSoftWraps(SoftWrapAppliancePlaces.MAIN_EDITOR);
  }

  public boolean isUseSoftWraps(@NotNull SoftWrapAppliancePlaces place) {
    return myPlacesToUseSoftWraps.contains(place);
  }

  public void setUseSoftWraps(boolean use) {
    setUseSoftWraps(use, SoftWrapAppliancePlaces.MAIN_EDITOR);
  }

  public void setUseSoftWraps(boolean use, @NotNull SoftWrapAppliancePlaces place) {
    boolean update = use ^ myPlacesToUseSoftWraps.contains(place);
    if (!update) {
      return;
    }

    if (use) {
      myPlacesToUseSoftWraps.add(place);
    }
    else {
      myPlacesToUseSoftWraps.remove(place);
    }
    storeRawSoftWraps();
    if (place == SoftWrapAppliancePlaces.MAIN_EDITOR) {
      setSoftWrapFileMasks(getSoftWrapFileMasks());
    }
  }

  public boolean isUseCustomSoftWrapIndent() {
    return myOptions.USE_CUSTOM_SOFT_WRAP_INDENT;
  }

  public void setUseCustomSoftWrapIndent(boolean use) {
    boolean old = myOptions.USE_CUSTOM_SOFT_WRAP_INDENT;
    if (old == use) return;
    myOptions.USE_CUSTOM_SOFT_WRAP_INDENT = use;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_USE_CUSTOM_SOFT_WRAP_INDENT, old, use);
  }

  public int getCustomSoftWrapIndent() {
    return myOptions.CUSTOM_SOFT_WRAP_INDENT;
  }

  public void setCustomSoftWrapIndent(int indent) {
    int old = myOptions.CUSTOM_SOFT_WRAP_INDENT;
    if (old == indent) return;
    myOptions.CUSTOM_SOFT_WRAP_INDENT = indent;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_CUSTOM_SOFT_WRAP_INDENT, old, indent);
  }

  public int getVerticalScrollOffset() {
    return myOptions.VERTICAL_SCROLL_OFFSET;
  }

  public void setVerticalScrollOffset(int offset) {
    int old = myOptions.VERTICAL_SCROLL_OFFSET;
    if (old == offset) return;
    myOptions.VERTICAL_SCROLL_OFFSET = offset;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_VERTICAL_SCROLL_OFFSET, old, offset);
  }

  public int getHorizontalScrollOffset() {
    return myOptions.HORIZONTAL_SCROLL_OFFSET;
  }

  public void setHorizontalScrollOffset(int offset) {
    int old = myOptions.HORIZONTAL_SCROLL_OFFSET;
    if (old == offset) return;
    myOptions.HORIZONTAL_SCROLL_OFFSET = offset;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_HORIZONTAL_SCROLL_OFFSET, old, offset);
  }

  public int getVerticalScrollJump() {
    return myOptions.VERTICAL_SCROLL_JUMP;
  }

  public void setVerticalScrollJump(int jump) {
    int old = myOptions.VERTICAL_SCROLL_JUMP;
    if (old == jump) return;
    myOptions.VERTICAL_SCROLL_JUMP = jump;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_VERTICAL_SCROLL_JUMP, old, jump);
  }

  public int getHorizontalScrollJump() {
    return myOptions.HORIZONTAL_SCROLL_JUMP;
  }

  public void setHorizontalScrollJump(int jump) {
    int old = myOptions.HORIZONTAL_SCROLL_JUMP;
    if (old == jump) return;
    myOptions.HORIZONTAL_SCROLL_JUMP = jump;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_HORIZONTAL_SCROLL_JUMP, old, jump);
  }

  public boolean isVirtualSpace() {
    return myOptions.IS_VIRTUAL_SPACE;
  }

  public void setVirtualSpace(boolean val) {
    boolean oldValue = myOptions.IS_VIRTUAL_SPACE;
    if (oldValue == val) return;

    myOptions.IS_VIRTUAL_SPACE = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_VIRTUAL_SPACE, oldValue, val);
  }

  public boolean isCaretInsideTabs() {
    return myOptions.IS_CARET_INSIDE_TABS;
  }

  public void setCaretInsideTabs(boolean val) {
    boolean old = myOptions.IS_CARET_INSIDE_TABS;
    if (old == val) return;
    myOptions.IS_CARET_INSIDE_TABS = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_CARET_INSIDE_TABS, old, val);
  }

  public boolean isBlinkCaret() {
    return myOptions.IS_CARET_BLINKING;
  }

  public void setBlinkCaret(boolean blinkCaret) {
    boolean old = myOptions.IS_CARET_BLINKING;
    if (old == blinkCaret) return;
    myOptions.IS_CARET_BLINKING = blinkCaret;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_CARET_BLINKING, old, blinkCaret);
  }

  public int getBlinkPeriod() {
    return BLINKING_RANGE.fit(myOptions.CARET_BLINKING_PERIOD);
  }

  public void setBlinkPeriod(int blinkInterval) {
    int newValue = BLINKING_RANGE.fit(blinkInterval);
    int old = myOptions.CARET_BLINKING_PERIOD;
    if (old == newValue) return;
    myOptions.CARET_BLINKING_PERIOD = newValue;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_CARET_BLINKING_PERIOD, old, newValue);
  }


  public boolean isEnsureNewLineAtEOF() {
    return myOptions.IS_ENSURE_NEWLINE_AT_EOF;
  }

  public void setEnsureNewLineAtEOF(boolean ensure) {
    boolean old = myOptions.IS_ENSURE_NEWLINE_AT_EOF;
    if (old == ensure) return;
    myOptions.IS_ENSURE_NEWLINE_AT_EOF = ensure;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_ENSURE_NEWLINE_AT_EOF, old, ensure);
  }

  public boolean isRemoveTrailingBlankLines() {
    return myOptions.REMOVE_TRAILING_BLANK_LINES;
  }

  public void setRemoveTrailingBlankLines(boolean remove) {
    boolean old = myOptions.REMOVE_TRAILING_BLANK_LINES;
    if (old == remove) return;
    myOptions.REMOVE_TRAILING_BLANK_LINES = remove;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_REMOVE_TRAILING_BLANK_LINES, old, remove);
  }

  @StripTrailingSpaces
  public String getStripTrailingSpaces() {
    return myOptions.STRIP_TRAILING_SPACES;
  } // TODO: move to CodeEditorManager or something else

  public void setStripTrailingSpaces(@StripTrailingSpaces String stripTrailingSpaces) {
    String old = myOptions.STRIP_TRAILING_SPACES;
    if (Objects.equals(old, stripTrailingSpaces)) return;
    myOptions.STRIP_TRAILING_SPACES = stripTrailingSpaces;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_STRIP_TRAILING_SPACES, old, stripTrailingSpaces);
  }

  public boolean isShowQuickDocOnMouseOverElement() {
    return myOptions.SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT && !GeneralSettings.getInstance().isSupportScreenReaders();
  }

  public void setShowQuickDocOnMouseOverElement(boolean show) {
    boolean old = myOptions.SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT;
    if (old == show) return;
    myOptions.SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT = show;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT, old, show);
  }


  public boolean isShowInspectionWidget() {
    return myOptions.SHOW_INSPECTION_WIDGET;
  }

  public void setShowInspectionWidget(boolean show) {
    boolean old = myOptions.SHOW_INSPECTION_WIDGET;
    if (old == show) return;
    myOptions.SHOW_INSPECTION_WIDGET = show;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_INSPECTION_WIDGET, old, show);
  }

  /**
   * @deprecated Use {@link #getTooltipsDelay()} instead
   */
  @Deprecated
  public int getQuickDocOnMouseOverElementDelayMillis() {
    return getTooltipsDelay();
  }

  public int getTooltipsDelay() {
    return TOOLTIPS_DELAY_RANGE.fit(myOptions.TOOLTIPS_DELAY_MS);
  }

  public void setTooltipsDelay(int delay) {
    int newValue = TOOLTIPS_DELAY_RANGE.fit(delay);
    int old = myOptions.TOOLTIPS_DELAY_MS;
    if (old == newValue) return;
    myOptions.TOOLTIPS_DELAY_MS = newValue;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_TOOLTIPS_DELAY_MS, old, newValue);
  }

  public boolean isShowIntentionBulb() {
    return myOptions.SHOW_INTENTION_BULB;
  }

  public void setShowIntentionBulb(boolean show) {
    boolean old = myOptions.SHOW_INTENTION_BULB;
    if (old == show) return;
    myOptions.SHOW_INTENTION_BULB = show;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_INTENTION_BULB, old, show);
  }

  public boolean isRefrainFromScrolling() {
    return myOptions.REFRAIN_FROM_SCROLLING;
  }

  public void setRefrainFromScrolling(boolean b) {
    boolean old = myOptions.REFRAIN_FROM_SCROLLING;
    if (old == b) return;
    myOptions.REFRAIN_FROM_SCROLLING = b;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_REFRAIN_FROM_SCROLLING, old, b);
  }

  public boolean isShowNotificationAfterReformat() {
    return Registry.is("editor.show.notification.after.reformat");
  }

  public boolean isShowNotificationAfterOptimizeImports() {
    return Registry.is("editor.show.notification.after.optimize.imports");
  }

  public boolean isWhitespacesShown() {
    return myOptions.IS_WHITESPACES_SHOWN;
  }

  public void setWhitespacesShown(boolean val) {
    boolean old = myOptions.IS_WHITESPACES_SHOWN;
    if (old == val) return;
    myOptions.IS_WHITESPACES_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_WHITESPACES_SHOWN, old, val);
  }

  public boolean isLeadingWhitespacesShown() {
    return myOptions.IS_LEADING_WHITESPACES_SHOWN;
  }

  public void setLeadingWhitespacesShown(boolean val) {
    boolean old = myOptions.IS_LEADING_WHITESPACES_SHOWN;
    if (old == val) return;
    myOptions.IS_LEADING_WHITESPACES_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_LEADING_WHITESPACES_SHOWN, old, val);
  }

  public boolean isInnerWhitespacesShown() {
    return myOptions.IS_INNER_WHITESPACES_SHOWN;
  }

  public void setInnerWhitespacesShown(boolean val) {
    boolean old = myOptions.IS_INNER_WHITESPACES_SHOWN;
    if (old == val) return;
    myOptions.IS_INNER_WHITESPACES_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_INNER_WHITESPACES_SHOWN, old, val);
  }

  public boolean isTrailingWhitespacesShown() {
    return myOptions.IS_TRAILING_WHITESPACES_SHOWN;
  }

  public void setTrailingWhitespacesShown(boolean val) {
    boolean old = myOptions.IS_TRAILING_WHITESPACES_SHOWN;
    if (old == val) return;
    myOptions.IS_TRAILING_WHITESPACES_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_TRAILING_WHITESPACES_SHOWN, old, val);
  }

  public boolean isSelectionWhitespacesShown() {
    return myOptions.IS_SELECTION_WHITESPACES_SHOWN;
  }

  public void setSelectionWhitespacesShown(boolean val) {
    boolean old = myOptions.IS_SELECTION_WHITESPACES_SHOWN;
    if (old == val) return;
    myOptions.IS_SELECTION_WHITESPACES_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_SELECTION_WHITESPACES_SHOWN, old, val);
  }

  public boolean isAllSoftWrapsShown() {
    return myOptions.IS_ALL_SOFTWRAPS_SHOWN;
  }

  public void setAllSoftwrapsShown(boolean val) {
    boolean old = myOptions.IS_ALL_SOFTWRAPS_SHOWN;
    if (old == val) return;
    myOptions.IS_ALL_SOFTWRAPS_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_ALL_SOFTWRAPS_SHOWN, old, val);
  }

  public boolean isIndentGuidesShown() {
    return myOptions.IS_INDENT_GUIDES_SHOWN;
  }

  public void setIndentGuidesShown(boolean val) {
    boolean old = myOptions.IS_INDENT_GUIDES_SHOWN;
    if (old == val) return;
    myOptions.IS_INDENT_GUIDES_SHOWN = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_INDENT_GUIDES_SHOWN, old, val);
  }

  public boolean isFocusMode() {
    return myOptions.IS_FOCUS_MODE;
  }

  public void setFocusMode(boolean val) {
    boolean old = myOptions.IS_FOCUS_MODE;
    if (old == val) return;
    myOptions.IS_FOCUS_MODE = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_FOCUS_MODE, old, val);
  }

  public boolean isSmoothScrolling() {
    return myOptions.IS_ANIMATED_SCROLLING;
  }

  public void setSmoothScrolling(boolean val) {
    boolean old = myOptions.IS_ANIMATED_SCROLLING;
    if (old == val) return;
    myOptions.IS_ANIMATED_SCROLLING = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_ANIMATED_SCROLLING, old, val);
  }

  public boolean isCamelWords() {
    return myOptions.IS_CAMEL_WORDS;
  }

  public void setCamelWords(boolean val) {
    boolean old = myOptions.IS_CAMEL_WORDS;
    if (old == val) return;
    myOptions.IS_CAMEL_WORDS = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_CAMEL_WORDS, old, val);
  }

  public boolean isAdditionalPageAtBottom() {
    return myOptions.ADDITIONAL_PAGE_AT_BOTTOM;
  }

  public void setAdditionalPageAtBottom(boolean val) {
    boolean old = myOptions.ADDITIONAL_PAGE_AT_BOTTOM;
    if (old == val) return;
    myOptions.ADDITIONAL_PAGE_AT_BOTTOM = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_ADDITIONAL_PAGE_AT_BOTTOM, old, val);
  }

  public boolean isDndEnabled() {
    return myOptions.IS_DND_ENABLED;
  }

  public void setDndEnabled(boolean val) {
    boolean old = myOptions.IS_DND_ENABLED;
    if (old == val) return;
    myOptions.IS_DND_ENABLED = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_DND_ENABLED, old, val);
  }

  public boolean isWheelFontChangeEnabled() {
    return myOptions.IS_WHEEL_FONTCHANGE_ENABLED;
  }

  public void setWheelFontChangeEnabled(boolean val) {
    boolean old = myOptions.IS_WHEEL_FONTCHANGE_ENABLED;
    if (old == val) return;
    myOptions.IS_WHEEL_FONTCHANGE_ENABLED = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_WHEEL_FONTCHANGE_ENABLED, old, val);
  }

  public boolean isWheelFontChangePersistent() {
    return myOptions.IS_WHEEL_FONTCHANGE_PERSISTENT;
  }

  public void setWheelFontChangePersistent(boolean val) {
    boolean old = myOptions.IS_WHEEL_FONTCHANGE_PERSISTENT;
    if (old == val) return;
    myOptions.IS_WHEEL_FONTCHANGE_PERSISTENT = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_WHEEL_FONTCHANGE_PERSISTENT, old, val);
  }

  public boolean isMouseClickSelectionHonorsCamelWords() {
    return myOptions.IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS;
  }

  public void setMouseClickSelectionHonorsCamelWords(boolean val) {
    boolean old = myOptions.IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS;
    if (old == val) return;
    myOptions.IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS, old, val);
  }

  public boolean isVariableInplaceRenameEnabled() {
    return myOptions.RENAME_VARIABLES_INPLACE;
  }

  public void setVariableInplaceRenameEnabled(final boolean val) {
    boolean old = myOptions.RENAME_VARIABLES_INPLACE;
    if (old == val) return;
    myOptions.RENAME_VARIABLES_INPLACE = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_RENAME_VARIABLES_INPLACE, old, val);
  }

  public boolean isPreselectRename() {
    return myOptions.PRESELECT_RENAME;
  }

  public void setPreselectRename(final boolean val) {
    boolean old = myOptions.PRESELECT_RENAME;
    if (old == val) return;
    myOptions.PRESELECT_RENAME = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_PRESELECT_RENAME, old, val);
  }

  public boolean isShowInlineLocalDialog() {
    return myOptions.SHOW_INLINE_DIALOG;
  }

  public void setShowInlineLocalDialog(final boolean val) {
    boolean old = myOptions.SHOW_INLINE_DIALOG;
    if (old == val) return;
    myOptions.SHOW_INLINE_DIALOG = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_INLINE_DIALOG, old, val);
  }

  public boolean addCaretsOnDoubleCtrl() {
    return myOptions.ADD_CARETS_ON_DOUBLE_CTRL;
  }

  public void setAddCaretsOnDoubleCtrl(boolean val) {
    boolean old = myOptions.ADD_CARETS_ON_DOUBLE_CTRL;
    if (old == val) return;
    myOptions.ADD_CARETS_ON_DOUBLE_CTRL = val;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_ADD_CARETS_ON_DOUBLE_CTRL, old, val);
  }

  public BidiTextDirection getBidiTextDirection() {
    return myOptions.BIDI_TEXT_DIRECTION;
  }

  public void setBidiTextDirection(BidiTextDirection direction) {
    BidiTextDirection old = myOptions.BIDI_TEXT_DIRECTION;
    if (old == direction) return;
    myOptions.BIDI_TEXT_DIRECTION = direction;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_BIDI_TEXT_DIRECTION, old, direction);
  }

  public boolean isShowIntentionPreview() {
    return myOptions.SHOW_INTENTION_PREVIEW;
  }

  public void setShowIntentionPreview(boolean show) {
    boolean old = myOptions.SHOW_INTENTION_PREVIEW;
    if (old == show) return;
    myOptions.SHOW_INTENTION_PREVIEW = show;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SHOW_INTENTION_PREVIEW, old, show);
  }

  public boolean isKeepTrailingSpacesOnCaretLine() {
    return myOptions.KEEP_TRAILING_SPACE_ON_CARET_LINE;
  }

  public void setKeepTrailingSpacesOnCaretLine(boolean keep) {
    boolean old = myOptions.KEEP_TRAILING_SPACE_ON_CARET_LINE;
    if (old == keep) return;
    myOptions.KEEP_TRAILING_SPACE_ON_CARET_LINE = keep;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_KEEP_TRAILING_SPACE_ON_CARET_LINE, old, keep);
  }

  public @NotNull String getSoftWrapFileMasks() {
    String storedValue = myOptions.SOFT_WRAP_FILE_MASKS;
    if (storedValue != null) {
      return storedValue;
    }
    return isUseSoftWraps() ? SOFT_WRAP_FILE_MASKS_ENABLED_DEFAULT : SOFT_WRAP_FILE_MASKS_DISABLED_DEFAULT;
  }

  public void setSoftWrapFileMasks(@NotNull String value) {
    String old = myOptions.SOFT_WRAP_FILE_MASKS;
    if (value.equals(old)) return;  // `value` is not null
    myOptions.SOFT_WRAP_FILE_MASKS = value;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_SOFT_WRAP_FILE_MASKS, old, value);
  }

  public @NotNull CaretStopOptions getCaretStopOptions() {
    return myOsSpecificState.CARET_STOP_OPTIONS;
  }

  public void setCaretStopOptions(@NotNull CaretStopOptions options) {
    myOsSpecificState.CARET_STOP_OPTIONS = options;
  }

  public boolean isUseEditorFontInInlays() {
    return myOptions.USE_EDITOR_FONT_IN_INLAYS;
  }

  public void setUseEditorFontInInlays(boolean value) {
    boolean old = myOptions.USE_EDITOR_FONT_IN_INLAYS;
    if (old == value) return;
    myOptions.USE_EDITOR_FONT_IN_INLAYS = value;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_USE_EDITOR_FONT_IN_INLAYS, old, value);
  }

  public boolean isInsertParenthesesAutomatically() {
    return myOptions.INSERT_PARENTHESES_AUTOMATICALLY;
  }

  public void setInsertParenthesesAutomatically(boolean value) {
    boolean old = myOptions.INSERT_PARENTHESES_AUTOMATICALLY;
    if (old == value) return;
    myOptions.INSERT_PARENTHESES_AUTOMATICALLY = value;
    myPropertyChangeSupport.firePropertyChange(PropNames.PROP_INSERT_PARENTHESES_AUTOMATICALLY, old, value);
  }

  public static final class PropNames {
    public static final @NonNls String PROP_USE_SOFT_WRAPS = "useSoftWraps";
    public static final @NonNls String PROP_SOFT_WRAP_FILE_MASKS = "softWrapFileMasks";
    public static final @NonNls String PROP_USE_CUSTOM_SOFT_WRAP_INDENT = "useCustomSoftWrapIndent";
    public static final @NonNls String PROP_CUSTOM_SOFT_WRAP_INDENT = "customSoftWrapIndent";
    // inconsistent name for backward compatibility because such constants are inlined at compilation stage
    public static final @NonNls String PROP_IS_VIRTUAL_SPACE = "VirtualSpace";
    public static final @NonNls String PROP_VERTICAL_SCROLL_OFFSET = "verticalScrollOffset";
    public static final @NonNls String PROP_VERTICAL_SCROLL_JUMP = "verticalScrollJump";
    public static final @NonNls String PROP_HORIZONTAL_SCROLL_OFFSET = "horizontalScrollOffset";
    public static final @NonNls String PROP_HORIZONTAL_SCROLL_JUMP = "horizontalScrollJump";
    public static final @NonNls String PROP_IS_CARET_INSIDE_TABS = "isCaretInsideTabs";
    public static final @NonNls String PROP_STRIP_TRAILING_SPACES = "stripTrailingSpaces";
    public static final @NonNls String PROP_IS_ENSURE_NEWLINE_AT_EOF = "isEnsureNewlineAtEof";
    public static final @NonNls String PROP_REMOVE_TRAILING_BLANK_LINES = "removeTrailingBlankLines";
    public static final @NonNls String PROP_SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT = "showQuickDocOnMouseOverElement";
    public static final @NonNls String PROP_SHOW_INSPECTION_WIDGET = "showInspectionWidget";
    public static final @NonNls String PROP_TOOLTIPS_DELAY_MS = "tooltipsDelayMs";
    public static final @NonNls String PROP_SHOW_INTENTION_BULB = "showIntentionBulb";
    public static final @NonNls String PROP_IS_CARET_BLINKING = "isCaretBlinking";
    public static final @NonNls String PROP_CARET_BLINKING_PERIOD = "caretBlinkingPeriod";
    public static final @NonNls String PROP_IS_RIGHT_MARGIN_SHOWN = "isRightMarginShown";
    public static final @NonNls String PROP_ARE_LINE_NUMBERS_SHOWN = "areLineNumbersShown";
    public static final @NonNls String PROP_ARE_LINE_NUMBERS_AFTER_ICONS = "areLineNumbersAfterIcons";
    public static final @NonNls String PROP_LINE_NUMERATION = "lineNumeration";
    public static final @NonNls String PROP_ARE_GUTTER_ICONS_SHOWN = "areGutterIconsShown";
    public static final @NonNls String PROP_IS_FOLDING_OUTLINE_SHOWN = "isFoldingOutlineShown";
    public static final @NonNls String PROP_IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER = "isFoldingOutlineShownOnlyOnHover";
    public static final @NonNls String PROP_IS_FOLDING_ENDINGS_SHOWN = "isFoldingEndingsShown";
    public static final @NonNls String PROP_SHOW_BREADCRUMBS_ABOVE = "showBreadcrumbsAbove";
    public static final @NonNls String PROP_SHOW_BREADCRUMBS = "showBreadcrumbs";
    public static final @NonNls String PROP_SHOW_STICKY_LINES = "showStickyLines";
    public static final @NonNls String PROP_SHOW_STICKY_LINES_PER_LANGUAGE = "showStickyLinesPerLanguage";
    public static final @NonNls String PROP_STICKY_LINES_LIMIT = "stickyLinesLimit";
    // inconsistent name for backward compatibility because such constants are inlined at compilation stage
    public static final @NonNls String PROP_ENABLE_RENDERED_DOC = "DocCommentRendering";
    public static final @NonNls String PROP_SHOW_INTENTION_PREVIEW = "showIntentionPreview";
    public static final @NonNls String PROP_USE_EDITOR_FONT_IN_INLAYS = "useEditorFontInInlays";
    public static final @NonNls String PROP_SMART_HOME = "smartHome";
    public static final @NonNls String PROP_IS_BLOCK_CURSOR = "isBlockCursor";
    public static final @NonNls String PROP_IS_FULL_LINE_HEIGHT_CURSOR = "isFullLineHeightCursor";
    public static final @NonNls String PROP_IS_HIGHLIGHT_SELECTION_OCCURRENCES = "isHighlightSelectionOccurrences";
    public static final @NonNls String PROP_IS_WHITESPACES_SHOWN = "isWhitespacesShown";
    public static final @NonNls String PROP_IS_LEADING_WHITESPACES_SHOWN = "isLeadingWhitespacesShown";
    public static final @NonNls String PROP_IS_INNER_WHITESPACES_SHOWN = "isInnerWhitespacesShown";
    public static final @NonNls String PROP_IS_TRAILING_WHITESPACES_SHOWN = "isTrailingWhitespacesShown";
    public static final @NonNls String PROP_IS_SELECTION_WHITESPACES_SHOWN = "isSelectionWhitespacesShown";
    public static final @NonNls String PROP_IS_ALL_SOFTWRAPS_SHOWN = "isAllSoftwrapsShown";
    public static final @NonNls String PROP_IS_INDENT_GUIDES_SHOWN = "isIndentGuidesShown";
    public static final @NonNls String PROP_IS_FOCUS_MODE = "isFocusMode";
    public static final @NonNls String PROP_IS_ANIMATED_SCROLLING = "isAnimatedScrolling";
    public static final @NonNls String PROP_IS_CAMEL_WORDS = "isCamelWords";
    public static final @NonNls String PROP_ADDITIONAL_PAGE_AT_BOTTOM = "additionalPageAtBottom";
    public static final @NonNls String PROP_IS_DND_ENABLED = "isDndEnabled";
    public static final @NonNls String PROP_IS_WHEEL_FONTCHANGE_ENABLED = "isWheelFontchangeEnabled";
    public static final @NonNls String PROP_IS_WHEEL_FONTCHANGE_PERSISTENT = "isWheelFontchangePersistent";
    public static final @NonNls String PROP_IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS = "isMouseClickSelectionHonorsCamelWords";
    public static final @NonNls String PROP_RENAME_VARIABLES_INPLACE = "renameVariablesInplace";
    public static final @NonNls String PROP_PRESELECT_RENAME = "preselectRename";
    public static final @NonNls String PROP_SHOW_INLINE_DIALOG = "showInlineDialog";
    public static final @NonNls String PROP_REFRAIN_FROM_SCROLLING = "refrainFromScrolling";
    public static final @NonNls String PROP_ADD_CARETS_ON_DOUBLE_CTRL = "addCaretsOnDoubleCtrl";
    public static final @NonNls String PROP_BIDI_TEXT_DIRECTION = "bidiTextDirection";
    public static final @NonNls String PROP_KEEP_TRAILING_SPACE_ON_CARET_LINE = "keepTrailingSpaceOnCaretLine";
    public static final @NonNls String PROP_INSERT_PARENTHESES_AUTOMATICALLY = "insertParenthesesAutomatically";
    // inconsistent name for backward compatibility because such constants are inlined at compilation stage
    public static final @NonNls String PROP_BREADCRUMBS_PER_LANGUAGE = "BreadcrumbsPerLanguage";
  }
}
