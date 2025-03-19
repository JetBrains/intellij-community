
package com.demo;
//
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class FieldWithSimilarAnnotation {
    private static final long serialVersionUID = -1L;

    @com.demo.NotNull
    @NotNull(groups = {SupplierSubmit.class})
    @Schema(description ="desc")
    private Long id;

}
