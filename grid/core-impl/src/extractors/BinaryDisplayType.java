package com.intellij.database.extractors;

import com.intellij.database.DataGridBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public enum BinaryDisplayType implements DisplayType {
  DETECT {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Detect.text");
    }
  },
  TEXT {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Text.text");
    }
  },
  HEX {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Hex.text");
    }
  },
  HEX_ASCII {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.Hex.ASCII.text");
    }
  },
  UUID {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.UUID.text");
    }
  },
  UUID_SWAP {
    @Override
    public @Nls @NotNull String getName() {
      return DataGridBundle.message("action.Console.TableResult.DisplayType.SwappedUUID.text");
    }
  }
}
