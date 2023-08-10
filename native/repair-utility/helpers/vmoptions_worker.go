package helpers

import (
	"errors"
	"os"
	"repair/logger"
	"strconv"
)

var CheckVmoptionsFileBool bool

func RestoreVmoptionsFile() (err error) {
	vmOptionsFile, err := GetVmOptionsFile()
	if err != nil {
		logger.InfoLogger.Println(err)
		return nil
	}
	newName := vmOptionsFile + ".old"
	err = os.Rename(vmOptionsFile, newName)
	logger.ExitWithExceptionOnError(err)
	logger.InfoLogger.Println("File has been moved to " + newName)
	return err
}

func CheckVmoptionsFile() {
	vmOptionsFile, err := GetVmOptionsFile()
	if err != nil {
		logger.InfoLogger.Println(err)
	}
	var errorsToBeFixed []error
	var warnings []error
	mappedOptions, garbageCollectorsInUse, vmoptionsDuplicates, err := ParseVmOptionsFile(vmOptionsFile)
	if err != nil {
		return
	}
	collectWarnings(getDuplicatedVmOptions(vmoptionsDuplicates), &warnings)
	collectWarnings(checkIfThereIsJavaAgent(mappedOptions), &warnings)
	collectErrorsToBeFixed(checkIfMoreThanOneGcInUse(garbageCollectorsInUse), &errorsToBeFixed)
	collectErrorsToBeFixed(checkXmxOn32Bit(mappedOptions["-Xmx"]), &errorsToBeFixed)
	collectErrorsToBeFixed(checkXmxVsXms(mappedOptions), &errorsToBeFixed)

	if len(warnings) > 0 {
		warn := errors.New("\n" + formatCollectedWarnings(warnings))
		logger.WarningLogger.Println(warn)
	}
	if len(errorsToBeFixed) > 0 {
		err = errors.New("\n" + formatCollectedErrors(errorsToBeFixed))
		logger.ErrorLogger.Println(err)
		if !IsInTests() || !CheckVmoptionsFileBool {
			suggestToFixVmoptionsFile(vmOptionsFile)
			checkVmoptionsFileAgain()
		}
	}
	if len(errorsToBeFixed) == 0 && len(warnings) == 0 {
		logger.InfoLogger.Println("VM options seems OK. No need to take actions.")
	}
}

func checkVmoptionsFileAgain() {
	logger.InfoLogger.Println("Checking .vmoptions file once again")
	CheckVmoptionsFileBoolVal := CheckVmoptionsFileBool
	CheckVmoptionsFileBool = true
	CheckVmoptionsFile()
	CheckVmoptionsFileBool = CheckVmoptionsFileBoolVal
}

func checkIfThereIsJavaAgent(options MappedVmoptions) error {
	if options["-javaagent"] != "" {
		return errors.New("Unknown java agent detected: " + options["-javaagent"])
	}
	return nil
}

func formatCollectedWarnings(warningsToBePrinted []error) string {
	return "The following WARNINGS were found during .vmoptions file checking: \n" + ConcatErrorsFromSlice(warningsToBePrinted)
}
func formatCollectedErrors(errorsToBeFixed []error) string {
	return "The following ERRORS were found during .vmoptions file checking: \n" + ConcatErrorsFromSlice(errorsToBeFixed)
}

func collectWarnings(warn error, warnings *[]error) {
	if warn != nil {
		*warnings = append(*warnings, warn)
	}
}

func collectErrorsToBeFixed(err error, errorsToBeFixed *[]error) {
	if err != nil {
		*errorsToBeFixed = append(*errorsToBeFixed, err)
	}
}

func checkIfMoreThanOneGcInUse(garbageCollectorsInUse []string) error {
	if len(garbageCollectorsInUse) > 1 {
		var gclist string
		for _, gc := range garbageCollectorsInUse {
			gclist += "\n\t" + gc
		}
		return errors.New("More than one Garbage Collector in use: " + gclist)
	}
	return nil
}

func checkXmxOn32Bit(xmxValue string) (err error) {
	var is64Bit = uint64(^uintptr(0)) == ^uint64(0)
	var xmxInBytes int
	if !is64Bit {
		xmxInBytes = toBytes(xmxValue)
		logger.ExitWithExceptionOnError(err)
		if xmxInBytes > 2576980377 {
			return errors.New("Xmx value is too large for 32-bit OS " + strconv.Itoa(xmxInBytes))
		}
	}
	return err
}
func checkXmxVsXms(options MappedVmoptions) (err error) {
	xmxInBytes := toBytes(options["-Xmx"])
	if xmxInBytes == -1 {
		return errors.New("Couldn't convert -Xmx=" + options["-Xmx"] + " to bytes. ")
	}
	if len(options["-Xms"]) < 1 {
		return errors.New("VM option 'Xms' is not set")
	}
	xmsInBytes := toBytes(options["-Xms"])
	if xmsInBytes == -1 {
		return errors.New("Couldn't convert -Xms=" + options["-Xms"] + " to bytes. ")
	}
	if xmxInBytes >= xmsInBytes {
		return nil
	} else {
		return errors.New("Xmx value (current value " + options["-Xmx"] + ") should be higher than Xms (current value " + options["-Xms"] + ")")
	}
}
func toBytes(initial string) (initialInBytes int) {
	if len(initial) < 1 {
		return -1
	}
	units := initial[len(initial)-1]
	initial = initial[:len(initial)-1]
	initialInt, _ := strconv.Atoi(initial)
	switch units {
	case 'k', 'K':
		initialInBytes = initialInt << 10
		return initialInBytes
	case 'm', 'M':
		initialInBytes = initialInt << 20
		return initialInBytes
	case 'g', 'G':
		initialInBytes = initialInt << 30
		return initialInBytes
	}
	return -1
}

func getDuplicatedVmOptions(vmoptionsDuplicates map[string]string) (err error) {
	if len(vmoptionsDuplicates) == 0 {
		return nil
	}
	var duplicatedOptions string
	for option := range vmoptionsDuplicates {
		duplicatedOptions += ("\n\t" + option)
	}
	err = errors.New("Duplicated VM options: " + duplicatedOptions)
	return err
}

func suggestToFixVmoptionsFile(file string) {
	question := "There are two options to fix the above error:\n" +
		"\t 1. Reset .vmoptions file to default (current .vmoptions file will be backed up) \n" +
		"\t 2. Open the .vmoptions file for editing and correct it manually \n" +
		"Please choose"
	options := []string{"1", "2"}
	answer := AskQuestionWithOptions(question, options)
	if answer == "1" || answer == "y" {
		logger.InfoLogger.Println("Restoring default .vmoptions file")
		logger.ExitWithExceptionOnError(RestoreVmoptionsFile())
	} else if answer == "2" {
		openVmoptionsEditor(file)
	}
}
func openVmoptionsEditor(vmOptionsFile string) {
	err := OpenFileInEditor(vmOptionsFile)
	logger.WriteToLogOnError(err, logger.ErrorLogger)
}
