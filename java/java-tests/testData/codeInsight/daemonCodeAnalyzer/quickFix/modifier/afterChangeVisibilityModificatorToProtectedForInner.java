// "Make 'Item' protected" "true"

import java.util.ArrayList;

class GenericImplementsPrivate extends ArrayList<GenericImplementsPrivate.Item> {
  protected class Item {
  }
}