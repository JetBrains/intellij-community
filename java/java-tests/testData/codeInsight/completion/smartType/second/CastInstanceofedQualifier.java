interface Zip extends Bar{
  Foo getZip();
}

interface Bar {}

class Foo {

}

class Goo {
    {
        Bar o;
        if (o instanceof Zip) {
          Foo f = <caret>
        }
    }
}

