package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsExElementFactory {
  public static JpsExElementFactory getInstance() {
    return InstanceHolder.INSTANCE;
  }

  public abstract <E extends JpsElement> JpsElementCollection<E> createCollection(JpsElementChildRole<E> role);

  public abstract JpsElementContainerEx createContainer(@NotNull JpsCompositeElementBase<?> parent);

  public abstract JpsElementContainerEx createContainerCopy(@NotNull JpsElementContainerEx original,
                                                            @NotNull JpsCompositeElementBase<?> parent);

  private static final class InstanceHolder {
    private static final JpsExElementFactory INSTANCE = JpsServiceManager.getInstance().getService(JpsExElementFactory.class);
  }
}
