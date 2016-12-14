package org.jetbrains.debugger.memory.utils;

public abstract class AbstractTableColumnDescriptor implements AbstractTableModelWithColumns.TableColumnDescriptor {
  private final String myName;
  private final Class<?> myClass;
  protected AbstractTableColumnDescriptor(String name, Class<?> elementClass) {
    myName = name;
    myClass = elementClass;
  }

  @Override
  public Class<?> getColumnClass() {
    return myClass;
  }


  @Override
  public String getName() {
    return myName;
  }
}
