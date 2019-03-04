// "Replace with Comparator chain" "true"

import java.util.*;

public class NodeDescriptor {

  public int getWeight() {
    return 0;
  }

  public int getIndex() {
    return 0;
  }

  public static final Comparator<NodeDescriptor> NODE_DESCRIPTOR_COMPARATOR = (descriptor1, descriptor2) <caret>-> {
    int compare = Integer.compare(descriptor1.getWeight(), descriptor2.getWeight());
    if (compare != 0) {
      return compare;
    }
    return Integer.compare(descriptor1.getIndex(), descriptor2.getIndex());
  };
}
