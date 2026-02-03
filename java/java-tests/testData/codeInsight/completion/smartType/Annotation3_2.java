@interface Column {
  String name();
}

@interface Table {
  Column id();
}

@interface Join {
  Table[] table();
}


@Join({@Table(id = <caret>)} )
@interface Annotation {

}