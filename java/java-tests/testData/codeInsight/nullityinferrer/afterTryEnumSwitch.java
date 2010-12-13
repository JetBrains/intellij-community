import org.jetbrains.annotations.*;

public class Infer {
    enum E {;
    }

    void trySwitchEnum(@NotNull E e) {
        switch (e) {

        }
    }

}