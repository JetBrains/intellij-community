package com.intellij.database.datagrid;

import com.intellij.database.run.ReservedCellValue;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Gregory.Shrago
*/
public interface DataConsumer {

  default void setColumns(@NotNull GridDataRequest.Context context, GridColumn @NotNull [] columns) {
    setColumns(context, 0, 0, columns, 0);
  }

  default void setColumns(@NotNull GridDataRequest.Context context, int subQueryIndex, int resultSetIndex,
                          GridColumn @NotNull [] columns, int firstRowNum) {
  }

  default void setInReference(@NotNull GridDataRequest.Context context, @NotNull Object reference) {
  }

  default void updateColumns(@NotNull GridDataRequest.Context context, GridColumn @NotNull [] columns) {
  }

  default void setOutReferences(@NotNull GridDataRequest.Context context, @NotNull Set<Object> references) {
  }

  default void addRows(@NotNull GridDataRequest.Context context, @NotNull List<? extends GridRow> rows) {
  }

  default void afterLastRowAdded(@NotNull GridDataRequest.Context context, int total) {
  }


  class Row implements GridRow {
    /** @deprecated use {@code GridRow.getRowNum()} instead */
    @Deprecated(forRemoval = true)
    public final int rowNum;

    /** @deprecated use {@code GridRow.getSize() and GridRow.getValue(int)} instead */
    @Deprecated(forRemoval = true)
    public final Object[] values;

    protected Row(final int rowNum, final Object[] values) {
      this.rowNum = rowNum;
      this.values = values;
    }

    public static Row create(int realIdx, Object[] values) {
      return new Row(realIdx + 1, values);
    }

    @Override
    public void setValue(int i, @Nullable Object object) {
      values[i] = object;
    }

    @Override
    public Object getValue(int columnNum) {
      return columnNum < values.length ? values[columnNum] : ReservedCellValue.UNSET;
    }

    @Override
    public int getSize() {
      return values.length;
    }

    @Override
    public int getRowNum() {
      return rowNum;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof GridRow)) return false;
      return GridRow.equals(this, (GridRow)o);
    }

    @Override
    public int hashCode() {
      int result = getRowNum();
      result = 31 * result + Arrays.hashCode(values);
      return result;
    }

    @Override
    public String toString() {
      return "Row{" +
             "rowNum=" + rowNum +
             ", values=" + Arrays.toString(values) +
             '}';
    }
  }

  class Column implements JdbcGridColumn {

    private final Set<Attribute> attributes;

    private final int columnNum;
    private final int type;
    private final String name;
    private final String typeName;
    private final String clazz;
    private final int precision;
    private final int scale;

    private final String catalog;
    private final String schema;
    private final String table;

    public Column(int columnNum, String name, int type, @Nullable String typeName, @Nullable String clazz) {
      this(columnNum, name, type, typeName, clazz, -1, -1, null, null, null);
    }

    public Column(int columnNum, String name, int type, @Nullable String typeName, @Nullable String clazz,
                  int precision, int scale, @Nullable String catalog, @Nullable String schema, @Nullable String table) {
      this(columnNum, name, type, typeName, clazz, precision, scale, catalog, schema, table, Collections.emptySet());
    }

    public Column(int columnNum, String name, int type, @Nullable String typeName, @Nullable String clazz,
                  int precision, int scale, @Nullable String catalog, @Nullable String schema, @Nullable String table,
                  @NotNull Set<Attribute> attributes) {
      this.columnNum = columnNum;
      this.name = name;
      this.type = type;
      this.typeName = typeName;
      this.clazz = clazz;
      this.precision = precision;
      this.scale = scale;

      this.catalog = catalog;
      this.schema = schema;
      this.table = table;
      this.attributes = attributes;
    }

    @Override
    public @Nullable String getJavaClassName() {
      return clazz;
    }

    @Override
    public int getScale() {
      return scale;
    }

    @Override
    public String toString() {
      return "Column" + getColumnNumber() + "{" +
             "name='" + getName() + '\'' +
             ", type=" + getType() +
             ", typeName='" + getTypeName() + '\'' +
             ", clazz='" + clazz + '\'' +
             ", table='" + catalog + '\'' + ".'" + schema + '\'' + ".'" + table + '\'' +
             '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Column column = (Column)o;

      if (getColumnNumber() != column.getColumnNumber()) return false;
      if (precision != column.precision) return false;
      if (scale != column.scale) return false;
      if (getType() != column.getType()) return false;
      if (!Objects.equals(catalog, column.catalog)) return false;
      if (!Objects.equals(clazz, column.clazz)) return false;
      if (!Objects.equals(getName(), column.getName())) return false;
      if (!Objects.equals(schema, column.schema)) return false;
      if (!Objects.equals(table, column.table)) return false;
      if (!Objects.equals(getTypeName(), column.getTypeName())) return false;
      if (!attributes.equals(column.attributes)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = getColumnNumber();
      result = 31 * result + getType();
      result = 31 * result + (getName() != null ? getName().hashCode() : 0);
      result = 31 * result + (getTypeName() != null ? getTypeName().hashCode() : 0);
      result = 31 * result + (clazz != null ? clazz.hashCode() : 0);
      result = 31 * result + precision;
      result = 31 * result + scale;
      result = 31 * result + (catalog != null ? catalog.hashCode() : 0);
      result = 31 * result + (schema != null ? schema.hashCode() : 0);
      result = 31 * result + (table != null ? table.hashCode() : 0);
      result = 31 * result + attributes.hashCode();
      return result;
    }

    @Override
    public int getType() {
      return type;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getTypeName() {
      return typeName;
    }

    @Override
    public int getSize() {
      return precision;
    }

    @Override
    public int getColumnNumber() {
      return columnNum;
    }

    @Override
    public @NotNull Set<Attribute> getAttributes() {
      return attributes;
    }

    @Override
    public @Nullable String getTable() {
      return table;
    }

    @Override
    public @Nullable String getSchema() {
      return schema;
    }

    @Override
    public @Nullable String getCatalog() {
      return catalog;
    }

    public static DataConsumer.Column copy(@NotNull GridColumn column, int idx) {
      return copy(column, idx, column.getName(), column.getType(), column.getTypeName());
    }

    public static DataConsumer.Column copy(@NotNull GridColumn column, int idx, @NotNull String name, int type, @Nullable String typeName) {
      Column consumerColumn = ObjectUtils.tryCast(column, Column.class);
      return consumerColumn == null
             ? new DataConsumer.Column(idx, name, type, typeName, null)
             : new DataConsumer.Column(idx, name, type, typeName, consumerColumn.getJavaClassName(),
                                       consumerColumn.getSize(), consumerColumn.getScale(), consumerColumn.getCatalog(),
                                       consumerColumn.getSchema(), consumerColumn.getTable(), consumerColumn.getAttributes());
    }
  }

  class Composite implements DataConsumer {
    private final List<DataConsumer> delegates;

    public Composite(List<DataConsumer> delegates) {
      this.delegates = delegates;
    }

    public Composite(DataConsumer... delegates) {
      this.delegates = Arrays.asList(delegates);
    }

    @Override
    public void setColumns(@NotNull GridDataRequest.Context context, int subQueryIndex, int resultSetIndex,
                           GridColumn @NotNull [] columns, int firstRowNum) {
      for (DataConsumer delegate : delegates) {
        delegate.setColumns(context, subQueryIndex, resultSetIndex, columns, firstRowNum);
      }
    }

    @Override
    public void setInReference(@NotNull GridDataRequest.Context context, @NotNull Object reference) {
      for (DataConsumer delegate : delegates) {
        delegate.setInReference(context, reference);
      }
    }

    @Override
    public void updateColumns(@NotNull GridDataRequest.Context context, GridColumn @NotNull [] columns) {
      for (DataConsumer delegate : delegates) {
        delegate.updateColumns(context, columns);
      }
    }

    @Override
    public void setOutReferences(@NotNull GridDataRequest.Context context, @NotNull Set<Object> references) {
      for (DataConsumer delegate : delegates) {
        delegate.setOutReferences(context, references);
      }
    }

    @Override
    public void addRows(@NotNull GridDataRequest.Context context, @NotNull List<? extends GridRow> rows) {
      for (DataConsumer delegate : delegates) {
        delegate.addRows(context, rows);
      }
    }

    @Override
    public void afterLastRowAdded(@NotNull GridDataRequest.Context context, int total) {
      for (DataConsumer delegate : delegates) {
        delegate.afterLastRowAdded(context, total);
      }
    }
  }
}
