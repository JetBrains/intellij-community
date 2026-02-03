
class TopGene<T> {
}

class MidRaw extends TopGene {
}

class BottomGene<T> extends MidRaw {
}

class GeneType<T extends TopGene<String>> {
}

class GeneUser {
  public void success(GeneType<? extends BottomGene<String>> p) {
  }

  public void fail(GeneType<<error descr="Type parameter '? super BottomGene<String>' is not within its bound; should extend 'TopGene<java.lang.String>'">? super BottomGene<String></error>> p) {
  }
}
