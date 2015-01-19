package com.intellij.openapi.util.diff.api;

import java.util.List;

public interface SuppressiveDiffTool extends DiffTool {
  List<Class<? extends DiffTool>> getSuppressedTools();
}
