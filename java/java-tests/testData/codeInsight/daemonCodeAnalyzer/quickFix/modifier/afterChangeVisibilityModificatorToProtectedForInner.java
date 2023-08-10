// "Make 'Item' protected" "true-preview"

import java.util.ArrayList;

class GenericImplementsPrivate extends ArrayList<GenericImplementsPrivate.Item> {
  protected class Item {
  }
}