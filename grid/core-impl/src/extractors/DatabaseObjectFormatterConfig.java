package com.intellij.database.extractors;

import com.intellij.database.settings.DataGridSettings;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

public class DatabaseObjectFormatterConfig implements ObjectFormatterConfig {
  private static final DatabaseObjectFormatterConfig JS_SCRIPT_CONFIG = new DatabaseObjectFormatterConfig(ObjectFormatterMode.JS_SCRIPT);
  private static final DatabaseObjectFormatterConfig SQL_SCRIPT_CONFIG = new DatabaseObjectFormatterConfig(ObjectFormatterMode.SQL_SCRIPT);
  private static final DatabaseObjectFormatterConfig DEFAULT_CONFIG = new DatabaseObjectFormatterConfig(ObjectFormatterMode.DEFAULT);
  private static final DatabaseObjectFormatterConfig JSON_CONFIG = new DatabaseObjectFormatterConfig(ObjectFormatterMode.JSON);
  private static final DatabaseObjectFormatterConfig DISPLAY_CONFIG = new DatabaseDisplayObjectFormatterConfig();

  private final ObjectFormatterMode mode;

  public DatabaseObjectFormatterConfig(@NotNull ObjectFormatterMode mode) {
    this.mode = mode;
  }

  @Override
  public @NotNull ObjectFormatterMode getMode() {
    return mode;
  }

  @Override
  public @Nullable DataGridSettings getSettings() {
    return null;
  }

  @Override
  public boolean isAllowedShowBigObjects() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DatabaseObjectFormatterConfig config = (DatabaseObjectFormatterConfig)o;
    return mode == config.mode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode);
  }

  public static ObjectFormatterConfig get(@NotNull ObjectFormatterMode mode) {
    return mode == ObjectFormatterMode.JS_SCRIPT ? JS_SCRIPT_CONFIG :
           mode == ObjectFormatterMode.SQL_SCRIPT ? SQL_SCRIPT_CONFIG :
           mode == ObjectFormatterMode.DEFAULT ? DEFAULT_CONFIG :
           mode == ObjectFormatterMode.JSON ? JSON_CONFIG :
           DISPLAY_CONFIG;


  }

  public static class DatabaseDisplayObjectFormatterConfig extends DatabaseObjectFormatterConfig implements DisplayObjectFormatterConfig {
    private final DisplayType displayType;
    private final boolean isModeDetectedAutomatically;

    private final Set<BinaryDisplayType> allowedTypes;

    private final DataGridSettings settings;

    private boolean isAllowedShowBigObjects;

    public DatabaseDisplayObjectFormatterConfig() {
      this(null, false, null, null);
    }

    public DatabaseDisplayObjectFormatterConfig(
      @Nullable DisplayType displayType,
      boolean isModeDetectedAutomatically,
      @Nullable Set<BinaryDisplayType> allowedTypes,
      @Nullable DataGridSettings settings
    ) {
      super(ObjectFormatterMode.DISPLAY);
      this.isAllowedShowBigObjects = false;
      this.displayType = displayType;
      this.isModeDetectedAutomatically = isModeDetectedAutomatically;
      this.allowedTypes = allowedTypes;
      this.settings = settings;
    }

    @Override
    public @Nullable DisplayType getDisplayType() {
      return displayType;
    }

    public boolean isModeDetectedAutomatically() {
      return isModeDetectedAutomatically;
    }

    public @Nullable Set<BinaryDisplayType> getAllowedTypes() {
      return allowedTypes;
    }

    @Override
    public @Nullable DataGridSettings getSettings() {
      return settings;
    }

    public void allowShowBigObjects() {
      this.isAllowedShowBigObjects = true;
    }

    @Override
    public boolean isAllowedShowBigObjects() {
      return this.isAllowedShowBigObjects;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      DatabaseDisplayObjectFormatterConfig config = (DatabaseDisplayObjectFormatterConfig)o;
      return isModeDetectedAutomatically == config.isModeDetectedAutomatically &&
             displayType == config.displayType &&
             Objects.equals(allowedTypes, config.allowedTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), displayType, isModeDetectedAutomatically, allowedTypes);
    }
  }

  public static @NotNull ObjectFormatterConfig toDisplayConfig(@NotNull ObjectFormatterConfig config) {
    DatabaseDisplayObjectFormatterConfig dbConfig = ObjectUtils.tryCast(config, DatabaseDisplayObjectFormatterConfig.class);
    DisplayType displayType = dbConfig != null ? dbConfig.getDisplayType() : BinaryDisplayType.DETECT;
    boolean isModeDetectedAutomatically = dbConfig != null && dbConfig.isModeDetectedAutomatically;
    Set<BinaryDisplayType> allowedTypes = dbConfig != null ? dbConfig.allowedTypes : null;
    return new DatabaseDisplayObjectFormatterConfig(displayType, isModeDetectedAutomatically, allowedTypes, config.getSettings());
  }

  public static boolean isTypeAllowed(@NotNull ObjectFormatterConfig config, @NotNull BinaryDisplayType type) {
    DatabaseDisplayObjectFormatterConfig dbConfig = ObjectUtils.tryCast(config, DatabaseDisplayObjectFormatterConfig.class);
    if (dbConfig == null) return true;
    DisplayType configType = dbConfig.getDisplayType();
    return configType == type || configType == BinaryDisplayType.DETECT ||
           configType == BinaryDisplayType.HEX && dbConfig.isModeDetectedAutomatically() && (dbConfig.getAllowedTypes() == null || dbConfig.getAllowedTypes().contains(type));
  }
}
