import java.util.List;
class Foreach {
    void foo (Foreach e) {
        for (String s : e.<caret>) {}
    }

    List<? extends String> f () {
        return null;
    }

}