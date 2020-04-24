// "Make 'Item' protected" "true"

import java.util.ArrayList;

class GenericImplementsPrivate extends ArrayList<caret><GenericImplementsPrivate.Item> {
  private class Item {
  }
}