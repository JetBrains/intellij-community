// "Make 'Item' protected" "true-preview"

import java.util.ArrayList;

class GenericImplementsPrivate extends ArrayList<caret><GenericImplementsPrivate.Item> {
  private class Item {
  }
}