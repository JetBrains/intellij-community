/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;

import java.util.Objects;

@Tag("instance-filter")
public class InstanceFilter implements JDOMExternalizable{
  public static final InstanceFilter[] EMPTY_ARRAY = new InstanceFilter[0];

  @Attribute("id")
  public long    ID      = 0;
  @Attribute("enabled")
  public boolean ENABLED = true;

  public InstanceFilter() {
  }

  protected InstanceFilter(long ID, boolean ENABLED) {
    this.ID = ID;
    this.ENABLED = ENABLED;
  }

  @Transient
  public long getId() {
    return ID;
  }

  @Transient
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    InstanceFilter filter = (InstanceFilter)o;

    if (ID != filter.ID) return false;
    if (ENABLED != filter.ENABLED) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(ID, ENABLED);
  }
}
