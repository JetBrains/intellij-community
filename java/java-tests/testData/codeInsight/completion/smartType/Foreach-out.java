import java.util.List;
class Foreach {
    void foo (Foreach e) {
        for (String s : e.f()) {}
    }

    List<? extends String> f () {
        return null;
    }

}