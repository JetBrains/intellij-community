class Foooo {
  interface Bar {}
}

class Bar {
    {
        Foooo c = new Foooo()<caret>
    }
}
