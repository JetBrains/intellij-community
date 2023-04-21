// "Use lombok @Getter for 'releaseDate'" "true"

import lombok.Getter;

import java.util.Date;

public class Foo {
    /**
     * -- GETTER --
     *  Returns The date.
     *  It's an instance field.
     *
     * @return The date
     * @return The value
     */
    @Getter
    private Date releaseDate;
  private int fieldWithoutGetter;

}