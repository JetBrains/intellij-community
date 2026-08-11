// "Put annotation attributes on separate lines" "true-preview"


class X {

  @ApiResponses(value = {
    @ApiResponse(responseCode = "200",
            description = "Success response",
            content = @Content(mediaType = PageAttributes.MediaType.APPLICATION_JSON, schema = @Schema(implementation = Procedure.class, type = "array"))),
    @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = PageAttributes.MediaType.APPLICATION_JSON))
  })
  void x() {}
}