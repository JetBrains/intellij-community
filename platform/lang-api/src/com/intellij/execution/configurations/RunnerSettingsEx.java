package com.intellij.execution.configurations;

import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class RunnerSettingsEx implements RunnerSettings {
  private final Set<String> mySerializedAccessorNameTracker = new THashSet<String>();
  private final SkipDefaultValuesSerializationFilters mySerializationFilter = new SkipDefaultValuesSerializationFilters() {
    @Override
    protected boolean accepts(@NotNull Accessor accessor, @NotNull Object bean, @Nullable Object beanValue) {
      if (mySerializedAccessorNameTracker.contains(accessor.getName())) {
        return true;
      }
      return super.accepts(accessor, bean, beanValue);
    }
  };

  @Override
  public final void readExternal(Element element) {
    mySerializedAccessorNameTracker.clear();
    XmlSerializer.deserializeInto(this, element, mySerializedAccessorNameTracker);
  }

  @Override
  public final void writeExternal(Element element) {
    XmlSerializer.serializeInto(this, element, mySerializationFilter);
  }
}