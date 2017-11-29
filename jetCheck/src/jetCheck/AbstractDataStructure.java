package jetCheck;

/**
 * @author peter
 */
abstract class AbstractDataStructure implements DataStructure {
  protected final StructureNode node;
  protected final int sizeHint;

  AbstractDataStructure(StructureNode node, int sizeHint) {
    this.node = node;
    this.sizeHint = sizeHint;
  }

  protected int childSizeHint() {
    return Math.max(1, sizeHint - 1);
  }

  @Override
  public int getSizeHint() {
    return sizeHint;
  }

}
