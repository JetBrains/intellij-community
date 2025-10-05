package sameParameterValue.localClassArgument.src;

import java.time.LocalDate;
import java.util.UUID;

class LocalClassArgument {
    private interface Data<T> {
        T data();
    }

    void testJavaLocalDate() {
        record Payload(LocalDate data) implements Data<LocalDate> {
        }
        var original = new Payload(LocalDate.now());
        assertSomething(Payload.class, original);
    }

    void testJavaUuid() {
        record Payload(UUID data) implements Data<UUID> {
        }
        var original = new Payload(UUID.randomUUID());
        assertSomething(Payload.class, original);
    }

    private <T> void assertSomething(final Class<? extends Data<T>> type, final Data<T> original) {
    }
}