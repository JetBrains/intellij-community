@interface Table {
  int columnCount();
}

@interface Join {
  Table table();
}


@Join(table = @Table(<caret>) )
@interface Annotation {

}