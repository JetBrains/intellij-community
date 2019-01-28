// "Replace with Comparator chain" "true"

import java.util.*;

public class NodeDescriptor {

  public int getWeight() {
    return 0;
  }

  public int getIndex() {
    return 0;
  }

  public static final Comparator<NodeDescriptor> NODE_DESCRIPTOR_COMPARATOR = Comparator.comparingInt(NodeDescriptor::getWeight).thenComparingInt(NodeDescriptor::getIndex);
}
