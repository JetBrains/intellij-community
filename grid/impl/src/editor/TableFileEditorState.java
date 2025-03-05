package com.intellij.database.editor;

import com.intellij.database.extractors.BinaryDisplayType;
import com.intellij.database.extractors.NumberDisplayType;
import com.intellij.lang.Language;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.database.datagrid.GridPagingModel.UNSET_PAGE_SIZE;

public class TableFileEditorState implements FileEditorState, Serializable {
  public static final int UNKNOWN_COLUMN_POSITION = 0;
  public static final int DEFAULT_OR_HIDDEN_COLUMN_POSITION = -1;
  @Attribute("lastOpenedTimestamp")
  public long lastOpenedTimestamp = 0;

  @Attribute("presentationMode")
  public String presentationMode = "";

  @Attribute("transposed")
  public boolean transposed = false;

  @Attribute("sessionName") public @Nls String sessionName = null;

  @Property(surroundWithTag = false)
  public Filter filter = new Filter();

  @Property(surroundWithTag = false)
  public Sorting mySorting = new Sorting();

  @XCollection(propertyElementName = "column-attributes", elementTypes = Column.class)
  public List<Column> columnAttributes = new ArrayList<>();

  @Attribute("projectRestartId")
  public String projectRestartId = null;

  @Attribute("pageSize")
  public int pageSize = UNSET_PAGE_SIZE;

  @Attribute("extractor")
  public String extractorFactoryId = "";

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
    return false;
  }

  public static int toSerializedPosition(int position) {
    if (position == -1) return position;
    return position + 1;
  }

  public static int fromSerializedPosition(int position) {
    if (position == -1) return position;
    return position - 1;
  }

  @Tag("filtering")
  public static class Filter implements Serializable {
    @Attribute("enabled")
    public boolean enabled = true;
    @Transient
    public String text = "";
    @Property(surroundWithTag = false)
    @XCollection(elementName = "filter", valueAttributeName = "text")
    public List<String> history = new ArrayList<>();
  }

  @Tag("sorting")
  public static class Sorting implements Serializable {
    @Transient
    public String text = "";
    @Property(surroundWithTag = false)
    @XCollection(elementName = "sort", valueAttributeName = "text")
    public List<String> history = new ArrayList<>();
  }

  @Tag("column")
  public static class Column implements Serializable {
    @Attribute("name")
    public String name = "";
    @Attribute("enabled")
    public boolean enabled = true;
    @Attribute("languageId")
    public String languageId = Language.ANY.getID();
    @Attribute("position")
    public int position;
    @Attribute("binaryDisplayType")
    public BinaryDisplayType binaryDisplayType;
    @Attribute("numberDisplayType")
    public NumberDisplayType numberDisplayType;
  }
}
