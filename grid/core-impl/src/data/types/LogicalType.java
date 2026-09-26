package com.intellij.database.data.types;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum LogicalType {
  TEXT_ID {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return switch (logicalType) {
        case BINARY_ID -> 103;
        case TEXT_UUID -> 102;
        case UUID -> 101;
        default -> super.getSuitability(logicalType);
      };
    }
  },
  UUID {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return switch (logicalType) {
        case TEXT_UUID -> 103;
        case TEXT_ID -> 102;
        case BINARY_ID -> 101;
        default -> super.getSuitability(logicalType);
      };
    }
  },
  TEXT_UUID {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return switch (logicalType) {
        case UUID -> 103;
        case TEXT_ID -> 102;
        case BINARY_ID -> 101;
        default -> super.getSuitability(logicalType);
      };
    }
  },
  BINARY_ID {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return switch (logicalType) {
        case TEXT_ID -> 103;
        case TEXT_UUID -> 102;
        case UUID -> 101;
        default -> super.getSuitability(logicalType);
      };
    }
  },
  NUMBER_RANGE,
  TIMESTAMP_RANGE,
  TIMESTAMP_TZ_RANGE,
  POINT,
  JSON,
  GEOMETRY,
  GEOGRAPHY,
  INTERVAL,
  TIMESTAMP_WITH_TZ_RANGE,
  DATE_RANGE,
  SERIAL,
  NTEXT {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, NVARCHAR, NCHAR) ? 4 :
             ArrayUtil.contains(logicalType, TEXT) ? 3 :
             ArrayUtil.contains(logicalType, CLOB) ? 2 :
             ArrayUtil.contains(logicalType, VARCHAR, CHAR) ? 1 :
             super.getSuitability(logicalType);
    }
  },
  TEXT {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, CLOB, JSON, XML) ? 4 :
             ArrayUtil.contains(logicalType, VARCHAR) ? 3 :
             ArrayUtil.contains(logicalType, CHAR) ? 2 :
             ArrayUtil.contains(logicalType, NTEXT, NVARCHAR, NCHAR) ? 1 :
             super.getSuitability(logicalType);
    }
  },
  CLOB {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, TEXT) ? 3 :
             ArrayUtil.contains(logicalType, JSON) ? 1 :
             super.getSuitability(logicalType);
    }
  },
  CHAR {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, TEXT, VARCHAR) ? 2 :
             ArrayUtil.contains(logicalType, NCHAR, NVARCHAR) ? 1 :
             super.getSuitability(logicalType);
    }
  },
  NCHAR {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, NTEXT, NVARCHAR) ? 2 :
             ArrayUtil.contains(logicalType, CHAR, VARCHAR) ? 1 :
             super.getSuitability(logicalType);
    }
  },
  VARCHAR {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, TEXT, CHAR) ? 2 :
             ArrayUtil.contains(logicalType, NVARCHAR, NCHAR) ? 1 :
             super.getSuitability(logicalType);
    }
  },
  NVARCHAR {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, NTEXT, NCHAR) ? 2 :
             ArrayUtil.contains(logicalType, VARCHAR, CHAR) ? 1 :
             super.getSuitability(logicalType);
    }
  },
  YEAR {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return logicalType == NUMBER ? 3 :
             logicalType == SERIAL ? 1 :
             super.getSuitability(logicalType);
    }
  },
  BINARY {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return logicalType == BLOB ? 1 : super.getSuitability(logicalType);
    }
  },
  VARBINARY {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return logicalType == BLOB ? 2 : super.getSuitability(logicalType);
    }
  },
  BLOB,
  GRAPHIC,
  BOOLEAN,
  FIXED_POINT_NUMBER  {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, MONEY) ? 3 :
             LogicalType.isSimilarNumbers(this, logicalType) ? LogicalType.getNumbersSimilarity(this, logicalType) :
             logicalType == YEAR ? 1 :
             super.getSuitability(logicalType);
    }
  },
  DOUBLE_PRECISION  {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return LogicalType.isSimilarNumbers(this, logicalType) ? LogicalType.getNumbersSimilarity(this, logicalType) :
             logicalType == YEAR ? 1 :
             super.getSuitability(logicalType);
    }
  },
  SINGLE_PRECISION  {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return LogicalType.isSimilarNumbers(this, logicalType) ? LogicalType.getNumbersSimilarity(this, logicalType) :
             logicalType == YEAR ? 1 :
             super.getSuitability(logicalType);
    }
  },
  DATE {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, TIMESTAMP, TIMESTAMP_WITH_TIMEZONE) ? 1 : super.getSuitability(logicalType);
    }
  },
  TIME,
  TIME_WITH_TIMEZONE {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return ArrayUtil.contains(logicalType, TIME) ? 100 : super.getSuitability(logicalType);
    }
  },
  TIMESTAMP {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return logicalType == TIMESTAMP_WITH_TIMEZONE ? 102 :
             logicalType == DATE ? 101 :
             super.getSuitability(logicalType);
    }
  },
  TIMESTAMP_WITH_TIMEZONE {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return logicalType == TIMESTAMP ? 2 :
             logicalType == DATE ? 1 :
             super.getSuitability(logicalType);
    }
  },
  BINARY_STRING,
  UNSIGNED_NUMBER {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return LogicalType.isSimilarNumbers(this, logicalType) ?
             LogicalType.getNumbersSimilarity(this, logicalType) :
             super.getSuitability(logicalType);
    }
  },
  NUMBER {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return LogicalType.isSimilarNumbers(this, logicalType) ? LogicalType.getNumbersSimilarity(this, logicalType) :
             logicalType == SERIAL ? 3 :
             logicalType == YEAR ? 1 :
             super.getSuitability(logicalType);
    }
  },
  XML,
  MONEY,
  INET,
  TSVECTOR,
  UNKNOWN {
    @Override
    public int getSuitability(@NotNull LogicalType logicalType) {
      return 0;
    }
  };

  public int getSuitability(@NotNull LogicalType logicalType) {
    return logicalType == this ? Integer.MAX_VALUE : 0;
  }

  private static int getNumbersSimilarity(@NotNull LogicalType original, @NotNull LogicalType coming) {
    return NumberCategory.of(original).similarity(NumberCategory.of(coming));
  }

  private static boolean isSimilarNumbers(@NotNull LogicalType original, @NotNull LogicalType coming) {
    NumberCategory originalCategory = NumberCategory.getIfPresent(original);
    NumberCategory comingCategory = NumberCategory.getIfPresent(coming);
    return originalCategory != null && comingCategory != null && original != coming;
  }

  public static boolean isText(@NotNull LogicalType type) {
    return type == TEXT_ID ||
           type == TEXT ||
           type == CHAR ||
           type == NCHAR ||
           type == VARCHAR ||
           type == NVARCHAR ||
           type == NTEXT;
  }

  public static boolean isNumeric(@NotNull LogicalType type) {
    return NumberCategory.getIfPresent(type) != null;
  }

  private enum NumberCategory {
    DECIMAL(SINGLE_PRECISION, DOUBLE_PRECISION, FIXED_POINT_NUMBER),
    INTEGER(UNSIGNED_NUMBER, NUMBER);

    private final LogicalType[] myTypes;

    NumberCategory(@NotNull LogicalType... types) {
      myTypes = types;
    }

    boolean isMine(@NotNull LogicalType type) {
      return ArrayUtil.contains(type, myTypes);
    }

    int similarity(@NotNull LogicalType.NumberCategory category) {
      return this == category ? 3 : 2;
    }

    static @Nullable LogicalType.NumberCategory getIfPresent(@NotNull LogicalType type) {
      for (NumberCategory category : values()) {
        if (category.isMine(type)) return category;
      }
      return null;
    }

    static @NotNull LogicalType.NumberCategory of(@NotNull LogicalType type) {
      for (NumberCategory category : values()) {
        if (category.isMine(type)) return category;
      }
      throw new IllegalArgumentException("Unexpected type: " + type);
    }
  }
}
