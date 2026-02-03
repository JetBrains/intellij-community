import java.lang.Exception;

class Foo {

  void m() {

    try {

    } catch (Exception e) {

    } <warning descr="Redundant block marker">//end try-catch</warning>

    try {

    } catch (Exception e) {

    } <warning descr="Redundant block marker">// endtrycatchblockmarker</warning>

    try {

    } catch (Exception e) {

    } <warning descr="Redundant block marker">// endtrycatchblockmarker</warning>

    try {

    } catch (Exception e) {

    } finally {

    } <warning descr="Redundant block marker">// end finally</warning>

    try {

    } catch (Exception e) {

    } finally {

    }
    // end
    // not a block marker

  }

}