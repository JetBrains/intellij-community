import java.util.*;

class Main {
  void test() {
    var x = new <error descr="Cannot resolve symbol 'Person'">Person</error>();
    var y = (new <error descr="Cannot resolve symbol 'Person'">Person</error>[10]);
    <error descr="Unknown class: 'Person'">var</error> z = getPerson();
    <error descr="Unknown class: 'Person'">var</error> w = getPeople();
    var l = getPeopleList();
  }
  
  native <error descr="Cannot resolve symbol 'Person'">Person</error> getPerson();
  native <error descr="Cannot resolve symbol 'Person'">Person</error>[] getPeople();
  native List<<error descr="Cannot resolve symbol 'Person'">Person</error>> getPeopleList();
}