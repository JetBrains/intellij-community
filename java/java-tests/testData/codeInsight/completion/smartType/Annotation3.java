@interface Table {
  int columnCount();
}

@interface Join {
  Table table();
}


@Join(table = <caret> )
@interface Annotation {

}