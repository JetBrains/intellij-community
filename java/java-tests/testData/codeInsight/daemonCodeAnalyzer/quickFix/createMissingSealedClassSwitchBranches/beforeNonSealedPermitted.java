// "Create missing switch branch 'Outer.Book'" "true-preview"
class Outer {
  interface SaleItem permits Book {
  }

  record Book(String title, String author, double price) implements SaleItem {
  }


  int calc(SaleItem item) {
    return switch (it<caret>em) {
    };
  }
}