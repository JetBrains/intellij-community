from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''animals = ['elephant', 'lion', "giraffe"]
print(animals)

animals += ["monkey", 'dog']
print(animals)

animals.append("dino")
print(animals)

replace 'dino' with 'dinosaur'
print(animals)''',
                     '''animals = ['elephant', 'lion', "giraffe"]
print(animals)

animals += ["monkey", 'dog']
print(animals)

animals.append("dino")
print(animals)


print(animals)''', "Use indexing and assignment")
