package com.intellij.database.extractors;

/**
 * Per-extractor configuration capabilities: the subset of {@link ExtractionConfig} options that a
 * given {@link DataExtractorFactory} supports.
 * Computed by {@link DataExtractorFactory#getApplicableOptions()}
 * and consumed by both the export UI (checkbox visibility) and the headless AI/MCP export path.
 */
public enum ExtractorConfigOption {
  ADD_COLUMN_HEADER,
  ADD_ROW_HEADER,
  TRANSPOSE,
  ADD_TABLE_DEFINITION,
  ADD_COMPUTED_COLUMNS,
  ADD_GENERATED_COLUMNS,
  ADD_QUERY,
}
