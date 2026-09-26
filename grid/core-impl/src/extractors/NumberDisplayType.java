package com.intellij.database.extractors;

import com.intellij.database.DataGridBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public enum NumberDisplayType implements DisplayType {
  NUMBER {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Number.text");
    }
  },
  TIMESTAMP_SECONDS {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Timestamp.Seconds.text");
    }
  },
  TIMESTAMP_MILLISECONDS {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Timestamp.Milliseconds.text");
    }
  },
  TIMESTAMP_MICROSECONDS {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Timestamp.Microseconds.text");
    }
  }
}
