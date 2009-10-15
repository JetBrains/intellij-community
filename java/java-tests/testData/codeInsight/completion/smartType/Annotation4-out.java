@interface Join {
  boolean nestedLoops();
}

@Join(nestedLoops = <caret> )
@interface Annotation {

}