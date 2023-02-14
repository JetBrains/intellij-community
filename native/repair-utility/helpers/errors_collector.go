package helpers

import "strconv"

func FormatCollectedWarnings(warningsToBePrinted []error) string {
	return "The following WARNINGS were found: \n" + ConcatErrorsFromSlice(warningsToBePrinted)
}
func FormatCollectedErrors(errorsToBeFixed []error) string {
	return "The following ERRORS were found: \n" + ConcatErrorsFromSlice(errorsToBeFixed)
}

func ConcatErrorsFromSlice(errorsToBePrinted []error) (errorText string) {
	for i, err := range errorsToBePrinted {
		errorText = errorText + (strconv.Itoa(i+1) + ". " + err.Error() + "\n")
	}
	return errorText
}
func CollectWarnings(warn error, warnings *[]error) {
	if warn != nil {
		*warnings = append(*warnings, warn)
	}
}
func CollectErrorsToBeFixed(err error, errorsToBeFixed *[]error) {
	if err != nil {
		*errorsToBeFixed = append(*errorsToBeFixed, err)
	}
}
