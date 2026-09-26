/**
 * Returns a {@code String} object representing this {@code UUID}.
 *
 * <p> The UUID string representation is as described by this BNF:
 * <blockquote><pre>
 * {@code
 * UUID                   = <time_low> "-" <time_mid> "-"
 *                          <time_high_and_version> "-"
 *                          <variant_and_sequence> "-"
 *                          <node>
 * time_low               = 4*<hexOctet>
 * time_mid               = 2*<hexOctet>
 * time_high_and_version  = 2*<hexOctet>
 * variant_and_sequence   = 2*<hexOctet>
 * node                   = 6*<hexOctet>
 * hexOctet               = <hexDigit><hexDigit>
 * hexDigit               =
 *       "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
 *       | "a" | "b" | "c" | "d" | "e" | "f"
 *       | "A" | "B" | "C" | "D" | "E" | "F"
 * }</pre></blockquote>
 */
public interface UUIDFromStandarLibrary<A> {}