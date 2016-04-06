class GoodCodeRed {
  public void foo(CategoryKey categoryKey) {
    Category current = getCategory(categoryKey);
  }

  public <T extends Category<T>> T getCategory(CategoryKey<T> key) {
    return null;
  }
}

interface Category<T extends Category> {
}

class CategoryKey<K extends Category> {
}