import org.jetbrains.annotations.NotNull;

public class Infer {
    enum E {;
    }

    void trySwitchEnum(@NotNull E e) {
        switch (e) {

        }
    }

}