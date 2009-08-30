package com.intellij.debugger;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.classFilter.ClassFilter;
import org.jdom.Element;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:49:27 PM
 */
public class InstanceFilter implements JDOMExternalizable{
  public static final InstanceFilter[] EMPTY_ARRAY = new InstanceFilter[0];
  
  public long    ID      = 0;
  public boolean ENABLED = true;

  protected InstanceFilter(long ID, boolean ENABLED) {
    this.ID = ID;
    this.ENABLED = ENABLED;
  }

  public long getId() {
    return ID;
  }

  public boolean isEnabled() {
    return ENABLED;
  }

  public void setId(long id) {
    ID = id;
  }

  public void setEnabled(boolean enabled) {
    ENABLED = enabled;
  }

  public static InstanceFilter create(String pattern) {
    return new InstanceFilter(Long.parseLong(pattern), true);
  }

  public static InstanceFilter create(final ClassFilter filter) {
    return new InstanceFilter(Long.parseLong(filter.getPattern()), filter.isEnabled());
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public static ClassFilter[] createClassFilters(InstanceFilter[] filters) {
    ClassFilter [] cFilters = new ClassFilter[filters.length];
    for (int i = 0; i < cFilters.length; i++) {
      InstanceFilter instanceFilter = filters[i];

      ClassFilter classFilter = new ClassFilter();
      classFilter.setEnabled(instanceFilter.isEnabled());
      classFilter.setPattern(Long.toString(instanceFilter.getId()));

      cFilters[i] = classFilter;
    }
    return cFilters;
  }
}
