package com.intellij.database.settings;

import com.intellij.database.csv.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@State(name = "CsvSettings", storages = @Storage(CsvSettings.STATE_NAME + ".xml"), category = SettingsCategory.TOOLS)
public final class CsvSettings implements PersistentStateComponent<CsvSettings>, ModificationTracker, CsvFormatsSettings {
  private static final int CURRENT_VERSION = 1;

  public static final String STATE_NAME = "csvSettings";

  private final AtomicLong myModificationCount = new AtomicLong();

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public static CsvSettings getSettings() {
    CsvSettings settings = ApplicationManager.getApplication().getService(CsvSettings.class);
    //loadState not called
    if (settings != null && settings.version == 0) {
      synchronized (settings) {
        settings.ensureDefaultsSet();
      }
    }
    return settings;
  }

  @Override
  public void fireChanged() {
    fireSettingsChanged();
  }

  public static void fireSettingsChanged() {
    CsvSettings instance = getSettings();
    instance.myModificationCount.incrementAndGet();
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.syncPublisher(CsvFormatsSettings.TOPIC).settingsChanged();
  }

  public static CsvSettings create() {
    CsvSettings settings = new CsvSettings();
    settings.ensureDefaultsSet();
    return settings;
  }

  private CsvSettings() {
  }

  @Override
  public @NotNull CsvSettings getState() {
    return this;
  }

  @TestOnly
  public CsvSettings copy() {
    return XmlSerializer.deserialize(XmlSerializer.serialize(this), CsvSettings.class);
  }

  @Override
  public void loadState(@NotNull CsvSettings state) {
    XmlSerializerUtil.copyBean(state, this);
    ensureDefaultsSet();
  }

  @Override
  public @NotNull List<CsvFormat> getCsvFormats() {
    List<CsvFormat> formats = getImmutableFormats(csvFormats);
    return formats.isEmpty() ? getDefaultFormats() : formats;
  }

  public static @NotNull List<CsvFormat> getDefaultFormats() {
    return Arrays.asList(
      CsvFormats.CSV_FORMAT.getValue(),
      CsvFormats.TSV_FORMAT.getValue(),
      CsvFormats.PIPE_SEPARATED_FORMAT.getValue(),
      CsvFormats.SEMICOLON_SEPARATED_FORMAT.getValue()
    );
  }

  @Override
  public void setCsvFormats(@NotNull List<CsvFormat> formats) {
    csvFormats = ContainerUtil.map(formats, PersistentCsvFormat::new);
  }

  private void ensureDefaultsSet() {
    if (getImmutableFormats(csvFormats).isEmpty()) {
      CsvFormatsSettings databaseSettings = CsvSettingsService.getDatabaseSettings();
      if (databaseSettings != null) {
        for (CsvFormat format : databaseSettings.getCsvFormats()) {
          csvFormats.add(new PersistentCsvFormat(format));
        }
      }
      else {
        setCsvFormats(getDefaultFormats());
      }
      myModificationCount.incrementAndGet();
    }
    version = CURRENT_VERSION;
  }

  public static void addNewFormat(@NotNull List<PersistentCsvFormat> csvFormats,
                                  @NotNull CsvFormat newFormat,
                                  @Nullable CsvFormat addAfterThis) {
    if (ContainerUtil.exists(csvFormats, f -> formatsSimilar(newFormat, f))) return;
    int tsvIndex = addAfterThis == null ? csvFormats.size() - 1 : ContainerUtil.indexOf(csvFormats, f -> f.id.equals(addAfterThis.id));
    csvFormats.add(tsvIndex + 1, new PersistentCsvFormat(newFormat));
  }

  public static boolean formatsSimilar(@NotNull CsvFormat format, @NotNull PersistentCsvFormat f) {
    if (StringUtil.equals(f.name, format.name)) return true;
    CsvFormat immutable = f.immutable();
    return CsvFormatsSettings.formatsSimilar(format, immutable);
  }

  private static @NotNull List<CsvFormat> getImmutableFormats(@NotNull List<PersistentCsvFormat> persistentFormats) {
    return ContainerUtil.mapNotNull(persistentFormats, format -> format == null ? null : format.immutable());
  }

  @Attribute("version")
  @Property(alwaysWrite = true)
  private int version = 0;

  @XCollection(propertyElementName = "csv-formats", elementName = "format", elementTypes = PersistentCsvFormat.class)
  public List<PersistentCsvFormat> csvFormats = new ArrayList<>();
}
