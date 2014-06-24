from test_helper import run_common_tests, import_file


def test_value(path):
    file = import_file(path)
    if file.first_half == '\nit is a reall':
        return "Bravo"
    return "Remember about string slicing."


if __name__ == '__main__':
    run_common_tests('''phrase = """
it is a really long string
"""
first_half = type here
print (first_half)
''', '''phrase = """
it is a really long string
"""
first_half =
print (first_half)
''', "You should modify the file")

    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    test_value(path)
