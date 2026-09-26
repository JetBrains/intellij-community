package helpers

import (
	"bufio"
	"os"
	"repair/logger"
	"strconv"
	"strings"
)

var (
	vmoptionsDelimeters             = []string{"=", ":", " "}
	listOfPossibleGarbageCollectors = []string{"-XX:+UseSerialGC", "-XX:+UseParallelGC", "-XX:+UseG1GC", "-XX:+UseShenandoahGC", "XX:+UseEpsilonGC", "-XX:+UseConcMarkSweepGC", "-XX:+UseZGC"}
	vmOptionsFileAsSlice            = make(MappedVmoptions)
)

type MappedVmoptions map[string]string

func GetVmoptionsAsSlice(vmOptionsFile string) (vmoptions MappedVmoptions) {
	var err error
	if len(vmOptionsFileAsSlice) == 0 {
		vmOptionsFileAsSlice, _, _, err = ParseVmOptionsFile(vmOptionsFile)
		logger.ExitWithExceptionOnError(err)
	}
	return vmOptionsFileAsSlice
}

func ParseVmOptionsFile(file string) (vmoptions MappedVmoptions, garbageCollectorsInUse []string, vmoptionsDuplicates map[string]string, err error) {
	vmoptions = make(MappedVmoptions)
	vmoptionsDuplicates = make(map[string]string)
	vmOptionsAsSlice, err := vmOptionsFileToSliceOfStrings(file)
	if err != nil {
		return nil, nil, nil, err
	}
	for _, option := range vmOptionsAsSlice {
		var optionName string
		var optionValue string
		optionName = getVmoptionName(option)
		optionValue = getVmoptionValue(option, optionName)
		collectGcInUse(option, &garbageCollectorsInUse)
		if currentOptionIsDuplicate(vmoptions, optionName) {
			vmoptionsDuplicates[optionName] = optionValue
		} else {
			vmoptions[optionName] = optionValue
		}
	}
	vmOptionsFileAsSlice = vmoptions
	return vmoptions, garbageCollectorsInUse, vmoptionsDuplicates, nil
}

func collectGcInUse(option string, garbageCollectorInUse *[]string) {
	for _, possibleGC := range listOfPossibleGarbageCollectors {
		if possibleGC == option {
			*garbageCollectorInUse = append(*garbageCollectorInUse, option)
		}
	}
}

func getVmoptionValue(option string, name string) (value string) {
	if isBoolean, isEnablingBoolean := isEnabingBooleanOption(option); isBoolean {
		if isEnablingBoolean {
			return "true"
		} else {
			return "false"
		}
	}
	value = strings.TrimPrefix(option, name)
	for _, delimiter := range vmoptionsDelimeters {
		value = strings.TrimSpace(strings.TrimPrefix(value, delimiter))
	}
	return value
}

func getVmoptionName(option string) string {
	if isBooleanOption(option) {
		return option
	}
	delimiterPosition := findDelimiterPositionOfVmoption(option)
	if delimiterPosition == -1 {
		return option
	} else {
		return option[:delimiterPosition]
	}
}

func vmOptionsFileToSliceOfStrings(vmOptionsFile string) (options []string, err error) {
	file, err := os.Open(vmOptionsFile)
	if err != nil {
		return nil, err
	}
	scanner := bufio.NewScanner(file)
	scanner.Split(bufio.ScanLines)
	var i int
	for scanner.Scan() {
		i++
		option := scanner.Text()
		if len(option) != 0 {
			if option[0] == '-' {
				options = append(options, option)
			} else if option[0] == '#' {
			} else {
				logger.DebugLogger.Println("There is a suspicious option: \"" + option + "\" at line " + strconv.Itoa(i))
			}
		}
	}
	err = file.Close()
	if err != nil {
		return nil, err
	}
	return options, err
}

func findDelimiterPositionOfVmoption(option string) (delimiterPosition int) {
	if strings.HasPrefix(option, "-Xmx") || strings.HasPrefix(option, "-Xms") || strings.HasPrefix(option, "-Xmn") {
		return 4
	}
	for _, delimeter := range vmoptionsDelimeters {
		delimiterPosition = strings.Index(option, delimeter)
		if delimiterPosition != -1 {
			return delimiterPosition
		}
	}
	return delimiterPosition
}

func isEnabingBooleanOption(option string) (isBoolean bool, isEnabling bool) {
	if strings.HasPrefix(option, "-XX:+") {
		return true, true
	}
	if strings.HasPrefix(option, "-XX:-") {
		return true, false
	}
	return false, false
}

func isBooleanOption(option string) (result bool) {
	if strings.HasPrefix(option, "-XX:-") || strings.HasPrefix(option, "-XX:+") {
		return true
	}
	return false
}

func currentOptionIsDuplicate(vmoptions MappedVmoptions, name string) bool {
	if vmoptions[name] != "" {
		return true
	}
	return false
}
