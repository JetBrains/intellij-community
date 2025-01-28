package com.intellij.database.settings;

import com.intellij.database.csv.CsvSettingsService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicLong;

@State(name = "DataGridAppearanceSettingsImpl", storages = @Storage(DataGridAppearanceSettingsImpl.STATE_NAME + ".xml"), category = SettingsCategory.UI)
public final class DataGridAppearanceSettingsImpl
  implements PersistentStateComponent<DataGridAppearanceSettingsImpl>, ModificationTracker, DataGridAppearanceSettings {
  private static final int CURRENT_VERSION = 1;

  static final String STATE_NAME = "dataViewsSettings";

  private final AtomicLong myModificationCount = new AtomicLong();

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }


  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public static DataGridAppearanceSettingsImpl getSettings() {
    DataGridAppearanceSettingsImpl settings = ApplicationManager.getApplication().getService(DataGridAppearanceSettingsImpl.class);
    //loadState not called
    if (settings != null && settings.version == 0) {
      synchronized (settings) {
        settings.ensureDefaultsSet();
      }
    }
    return settings;
  }

  public void fireChanged() {
    fireSettingsChanged();
  }

  public static void fireSettingsChanged() {
    DataGridAppearanceSettingsImpl instance = getSettings();
    instance.myModificationCount.incrementAndGet();
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.syncPublisher(DataGridAppearanceSettings.TOPIC).settingsChanged();
  }

  public static DataGridAppearanceSettings create() {
    DataGridAppearanceSettingsImpl settings = new DataGridAppearanceSettingsImpl();
    settings.ensureDefaultsSet();
    return settings;
  }

  private DataGridAppearanceSettingsImpl() {
  }

  @Override
  public @NotNull DataGridAppearanceSettingsImpl getState() {
    return this;
  }

  @TestOnly
  public DataGridAppearanceSettingsImpl copy() {
    return XmlSerializer.deserialize(XmlSerializer.serialize(this), DataGridAppearanceSettingsImpl.class);
  }

  @Override
  public void loadState(@NotNull DataGridAppearanceSettingsImpl state) {
    XmlSerializerUtil.copyBean(state, this);
    ensureDefaultsSet();
  }

  private void ensureDefaultsSet() {
    if (version < 1) {
      DataGridAppearanceSettings databaseSettings = ObjectUtils.tryCast(CsvSettingsService.getDatabaseSettings(), DataGridAppearanceSettings.class);
      if (databaseSettings != null) {
        setStripedTable(databaseSettings.isStripedTable());
        setUseGridCustomFont(databaseSettings.getUseGridCustomFont());
        setGridFontFamily(databaseSettings.getGridFontFamily());
        setGridFontSize(databaseSettings.getGridFontSize());
        setGridLineSpacing(databaseSettings.getGridLineSpacing());
        setBooleanMode(databaseSettings.getBooleanMode());
        myModificationCount.incrementAndGet();
      }
    }
    version = CURRENT_VERSION;
  }

  @Override
  public boolean getUseGridCustomFont() {
    return useGridCustomFont;
  }

  @Override
  public void setUseGridCustomFont(boolean value) {
    useGridCustomFont = value;
  }

  @Override
  public @Nullable String getGridFontFamily() {
    return gridFontFamily;
  }

  @Override
  public void setGridFontFamily(@Nullable String value) {
    gridFontFamily = value;
  }

  @Override
  public int getGridFontSize() {
    return gridFontSize;
  }

  @Override
  public void setGridFontSize(int value) {
    gridFontSize = value;
  }

  @Override
  public float getGridLineSpacing() {
    return gridLineSpacing;
  }

  @Override
  public void setGridLineSpacing(float value) {
    gridLineSpacing = value;
  }

  @Override
  public boolean isStripedTable() {
    return stripedTable;
  }

  @Override
  public void setStripedTable(boolean striped) {
    this.stripedTable = striped;
  }

  @Override
  public BooleanMode getBooleanMode() {
    return booleanMode;
  }

  @Override
  public void setBooleanMode(@NotNull BooleanMode mode) {
    booleanMode = mode;
  }

  @Attribute("version")
  @Property(alwaysWrite = true)
  private int version = 0;

  @OptionTag("striped-table")
  public boolean stripedTable = false;
  @OptionTag("use-custom-font")
  public boolean useGridCustomFont = false;
  @OptionTag("grid-font-family")
  public String gridFontFamily = null;
  @OptionTag("grid-font-size")
  public int gridFontSize = -1;
  @OptionTag("grid-line-spacing")
  public float gridLineSpacing = -1;
  @OptionTag("boolean-mode")
  public BooleanMode booleanMode = BooleanMode.TEXT;
}
